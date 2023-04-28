import sys
import pandas as pd
import argparse
import matplotlib.pyplot as plt
import matplotlib as mpl

'''
Get file names
'''
def get_filename():
    inputfile = ''
    parser = argparse.ArgumentParser(prog="CARMACloud Analysis for ERV BSM")
    parser.add_argument('--input', type=str, required=True)
    parser.add_argument('--sheet_names', type=str, required=True,
                        help='FER-13-1-1,FER-14,FER-15,FER-TBD-1')
    args = parser.parse_args()
    print(f'Received excel file: {args.input}')
    inputfile = args.input
    sheet_names = args.sheet_names
    return inputfile, sheet_names


'''Create time series line plot based on x,y values and labels'''
def create_line_plot(xlabel, ylabel, title, figure_name, df):    
    fig, ax1 = plt.subplots(figsize=(15, 10)) 
    ax1.xaxis.set_major_locator(plt.MaxNLocator(3))
    ax1.plot(df[xlabel], df[ylabel])
    plt.xlabel(xlabel)
    plt.ylabel(ylabel)
    plt.title(title)
    print(f'{figure_name}: Generated line chart')
    plt.savefig(figure_name,bbox_inches='tight')


'''Create  bar plot based on x,y values and labels'''
def create_bar_plot(xlabel, ylabel, title, figure_name, dict_to_plot):
    fig, ax = plt.subplots(figsize=(10, 5))
    bar = ax.bar(list(dict_to_plot.keys()),
                    list(dict_to_plot.values()))
    plt.tight_layout()
    ax.set_ylabel(ylabel)
    ax.set_xlabel(xlabel)
    ax.set_title(title)
    ax.bar_label(bar)
    print(f'{figure_name}: Generated bar chart')
    plt.savefig(figure_name,bbox_inches='tight')

'''
Comparing the content between two dataframe to find matching value, calculating the delay based on the logged timestamp of the matching values
'''
def get_delay_between_two_dataframe (source_df, source_time_lbl, source_value_lbl, target_df, target_time_lbl, target_value_lbl, return_time_lbl, return_value_lbl):
    fields_dict = {return_time_lbl: [], return_value_lbl: []}
    total_target = len(target_df.index)
    count = 0
    delay_threshold = 100 # Unit of millisecond
    for target_index, target_row in  target_df.iterrows():
        target = target_row[target_value_lbl]
        target_time_str = target_row[target_time_lbl]
        target_datetime =  pd.to_datetime(target_time_str)
        is_match_found = False
        for source_index, source_row in source_df.iterrows():
            source = source_row[source_value_lbl]
            if source.strip() == target.strip():
                target_datetime =  pd.to_datetime(target_time_str)
                source_time_str = source_row[source_time_lbl]
                receive_bsm_datetime = pd.to_datetime(source_time_str)
                # Only interested in second and microseconds. ignore date
                # BSM has to be received by carma-cloud from RSU first before carma-cloud send it back to RSU
                target_milli_of_day = target_datetime.hour * 3600* 1000 + target_datetime.minute * 60* 1000 + target_datetime.second* 1000 + target_datetime.microsecond/1000
                source_milli_of_day= receive_bsm_datetime.hour * 3600* 1000 + receive_bsm_datetime.minute * 60* 1000 + receive_bsm_datetime.second * 1000 + receive_bsm_datetime.microsecond /1000
                delay_milli = target_milli_of_day - source_milli_of_day
                if  delay_milli >= 0 and delay_milli < delay_threshold:      
                    fields_dict[return_time_lbl].append(target_time_str)
                    fields_dict[return_value_lbl].append(delay_milli)
                    count += 1
                    # print(f'Compared count {count}. {total_target-count} to go.')
                    is_match_found = True
                    source_df = source_df.drop(source_index)   
                    break
        if not is_match_found:
            print(f'ERROR: No match found for {target_value_lbl}: {target_time_str} {target_datetime}: {target.strip()} Need manual look up this record.')
    if count != total_target:
        print(f'WARNING: BSM to compare num  {total_target} != BSM matched record {count}. Increasing the no_duplication_period in the remove and clean up func to remove the same BSM that is sent to multiple RSUs.')  
    else:
        print(f'Delay calculation done!')
    fields_df = pd.DataFrame(fields_dict)
    return fields_df

'''
Remove duplicated successfully sent BSM as the same BSM are sent to multiple RSUs and logged the duplicated BSM in the terminal log
'''
def remove_duplicate_bsm_sent_success_record(df, no_duplication_period):
    time_lbl = df.columns[0]
    port_lbl = df.columns[1]
    bsm_lbl = df.columns[2]
    bsm_fields_dict = {time_lbl : [],  bsm_lbl: [] , port_lbl: []}
    num_of_dup = 0
    for row_index, row in  df.iterrows():
        if row[bsm_lbl] not in bsm_fields_dict[bsm_lbl]:
            bsm_fields_dict[time_lbl].append(row[time_lbl])
            bsm_fields_dict[bsm_lbl].append(row[bsm_lbl])
            bsm_fields_dict[port_lbl].append(row[port_lbl])
        else:
            bsm_index = 0
            is_dup = False
            # check if this row is duplicated
            for bsm in bsm_fields_dict[bsm_lbl]:
                if row[bsm_lbl] == bsm:
                    bsm_datetime =  pd.to_datetime(bsm_fields_dict[time_lbl][bsm_index])
                    row_datetime =  pd.to_datetime(row[time_lbl])
                    row_milli_of_day = row_datetime.hour * 3600* 1000 + row_datetime.minute * 60* 1000 + row_datetime.second* 1000 + row_datetime.microsecond/1000
                    bsm_milli_of_day= bsm_datetime.hour * 3600* 1000 + bsm_datetime.minute * 60* 1000 + bsm_datetime.second * 1000 + bsm_datetime.microsecond /1000
                    dup_milli = abs(row_milli_of_day - bsm_milli_of_day)
                    if dup_milli <= no_duplication_period and bsm_fields_dict[port_lbl][bsm_index] != row[port_lbl]:
                        bsm_fields_dict[time_lbl][bsm_index] = row[time_lbl] # if there is duplicate, update existing record with latest timestamp
                        bsm_fields_dict[port_lbl][bsm_index] = row[port_lbl]
                        is_dup = True
                        num_of_dup += 1
                bsm_index += 1            
            if not is_dup:
                bsm_fields_dict[time_lbl].append(row[time_lbl])
                bsm_fields_dict[bsm_lbl].append(row[bsm_lbl])
                bsm_fields_dict[port_lbl].append(row[port_lbl])
    print(f'Total bsm: {len(df.index)}')    
    print(f'Total duplicate: {num_of_dup}')    
    fields_df = pd.DataFrame(bsm_fields_dict)
    print(f'Total unique within period ({no_duplication_period} ms): {len(fields_df.index)}')  
    return fields_df
'''
Main entrypoint to read the excel file and generate plots for metrics: FER-13, FER-14, adn FER-15.
This plot data is generated by scripts: carmacloud_log_analysis.py and carmacloud_calc_BSM_forwarding_delay.py
'''
def main():
    inputfile, sheet_names_input_str = get_filename()
    sheet_names_input = sheet_names_input_str.split(',')
    FER_14_field_dicts = {}
    FER_15_field_dicts = {}
    df_sheet_bsm_identify_rsu = {}
    df_sheet_received_bsm = {}
    df_sheet_ignored_bsm = {}
    df_sheet_bsm_sent = {}
    # Read excel file and see all sheet names
    xl = pd.ExcelFile(inputfile)
    all_sheet_names = xl.sheet_names
    if bool(set(all_sheet_names).isdisjoint(sheet_names_input)):
        print(
            f'ERROR: Sheet names {sheet_names_input_str} not found! Provide correct sheet names with comma sperated.')
        exit()
    print(f'Reading sheet {sheet_names_input}. \nGenerating plots...')
    for sheet_name in sheet_names_input:
        sheet_name = sheet_name.strip()
        try:
            ''' 
            Reading sheet FER-13-1 and FER-13-2. Create subplots for BSM request parser delay and BSM process delay 
            '''
            if sheet_name == 'FER-13-1' or sheet_name == 'FER-13-2':
                if sheet_name == 'FER-13-1':
                    df_sheet = pd.read_excel(inputfile, sheet_name=sheet_name)
                    xlabel = df_sheet.columns[0]
                    ylabel = df_sheet.columns[1]
                    title = 'BSM request and parse delay (ms)'
                    figire_name = sheet_name
                    create_line_plot(xlabel, ylabel, title, figire_name, df_sheet)
                elif sheet_name == 'FER-13-2':
                    df_sheet = pd.read_excel(inputfile, sheet_name=sheet_name)
                    xlabel = df_sheet.columns[0]
                    ylabel = df_sheet.columns[1]
                    title = 'BSM request processing and identifying RSUs delay (ms)'
                    figire_name = sheet_name
                    create_line_plot(xlabel, ylabel, title,figire_name, df_sheet)

            '''
            Metric FER-13: Reading sheet FER-13-1-1 and FER-15. Generate time series for overall BSM delay
            '''
            if sheet_name == 'FER-13-1-1':
                excel_sheet_receive_bsm = pd.read_excel(inputfile, sheet_name='FER-13-1-1')
                excel_sheet_send_bsm_successfully = pd.read_excel(inputfile, sheet_name='FER-15')
                xlabel = "Time (UTC)"
                ylabel = "carma-cloud receives and successfully forwards BSM delay (ms)"
                print('FER-13: Clean up and remove duplicated BSM sent success...')
                # Remove duplicate BSM from send successfully list as the same BSM is sent to multiple identified RSUs
                no_duplication_period = 5 # Unit of millisecond              
                excel_sheet_send_bsm_successfully_no_dup = remove_duplicate_bsm_sent_success_record(excel_sheet_send_bsm_successfully, no_duplication_period)
                with pd.ExcelWriter("bsm_sent_successfully_no_dup.xlsx") as writer:
                    excel_sheet_send_bsm_successfully_no_dup.to_excel(writer, sheet_name='bsm_sent_successfully_no_dup', index=False)
                    print(f'Generated sheet for bsm_sent_successfully_no_dup' )
                print('FER-13: Calculating delay...')                
                df = get_delay_between_two_dataframe(excel_sheet_receive_bsm, 'Time (UTC)', 'Received: BSMRequest', excel_sheet_send_bsm_successfully_no_dup, 'Time (UTC)', 'Successfully_sent_BSM_Hex', xlabel, ylabel)
                figire_name = 'FER-13'
                create_line_plot(xlabel,  ylabel, 'CARMA Cloud processing BSM total delay (ms)', figire_name, df)
                with pd.ExcelWriter(figire_name+".xlsx") as writer:
                    df.to_excel(writer, sheet_name=figire_name, index=False)
                    print(f'Generated sheet for metric: {figire_name}' )

            '''
            Metric FER-14: Reading sheet FER-13-1-1, FER-TBD-1 and FER-14. Generate field dictionaries for the number of BSM received, BSM ignored, BSM identified RSU
            '''
            if sheet_name == 'FER-13-1-1':
                df_sheet_received_bsm = pd.read_excel(
                    inputfile, sheet_name=sheet_name)
                print(
                    f'Number of BSM received by carma-cloud: {len(df_sheet_received_bsm.index)}')
                FER_14_field_dicts['Received BSM'] = len(
                    df_sheet_received_bsm.index)
            elif sheet_name == 'FER-14':
                df_sheet_bsm_identify_rsu = pd.read_excel(
                    inputfile, sheet_name=sheet_name)
                print(
                    f'FER-14: Number of BSM processed to identify RSU locations: {len(df_sheet_bsm_identify_rsu.index)}')
                FER_14_field_dicts['BSMs used to identify RSUs'] = len(
                    df_sheet_bsm_identify_rsu.index)
            elif sheet_name == 'FER-TBD-1':
                df_sheet_ignored_bsm = pd.read_excel(
                    inputfile, sheet_name=sheet_name)
                print(
                    f'FER-14: Number of Ignored BSM: {len(df_sheet_ignored_bsm.index)}')
                FER_14_field_dicts['Ignored BSM'] = len(
                    df_sheet_ignored_bsm.index)

            '''
            Metric FER-15: Reading sheet FER-14 and FER-15. BSM identified RSU and BSM sent to the identified RSU successfully
            '''
            if sheet_name == 'FER-14':
                if len(df_sheet_bsm_identify_rsu.index) == 0:
                    df_sheet_bsm_identify_rsu = pd.read_excel(
                        inputfile, sheet_name=sheet_name)
                Identified_numbers_of_RSU = df_sheet_bsm_identify_rsu[df_sheet_bsm_identify_rsu.columns[1]]
                total_num_identified_rsu = 0
                for num in Identified_numbers_of_RSU:
                    total_num_identified_rsu += num
                print(
                    f'FER-14: Total number of RSU locations identified: {total_num_identified_rsu}')
                FER_15_field_dicts['Total num of RSU identified'] = total_num_identified_rsu
            elif sheet_name == 'FER-15':
                df_sheet_bsm_sent = pd.read_excel(
                    inputfile, sheet_name=sheet_name)
                df_sheet_bsm_sent_successfully = df_sheet_bsm_sent[df_sheet_bsm_sent.columns[2]]
                total_num_bsm_sent_successfully = len(
                    df_sheet_bsm_sent_successfully.index)
                print(
                    f'FER-14: Total number of BSMs sent successfully: {total_num_bsm_sent_successfully}')
                FER_15_field_dicts['Total num of BSMs successfully forwarded to identified RSUs'] = total_num_identified_rsu
        except ValueError as a:
            print(f'{a}')

    """*** PLOT *** """
    '''Metric FER-14: Generate bar chart for the number of BSM received, BSM ignored, BSM identified RSU'''
    if len(FER_14_field_dicts.keys()) == 3:
        create_bar_plot('BSM status', 'Number of BSMs', 'FER-14: CARMA Cloud identifies location of the RSUs along the ERV\'s future route.', 'FER-14', FER_14_field_dicts)

    '''Metric FER-15: Generate bar chart for CARMA Cloud forwards the latest ERV BSM to all RSUs in the extracted list.'''
    if len(FER_15_field_dicts.keys()) == 2:
        create_bar_plot('BSM status', 'Number of BSMs', 'FER-15: CARMA Cloud forwards the latest ERV BSM to all RSUs in the extracted list.', 'FER-15', FER_15_field_dicts)


if __name__ == '__main__':
    main()
