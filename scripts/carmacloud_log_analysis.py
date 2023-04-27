import sys
import pandas as pd
import argparse

'''
Get file names
'''
def get_filenames():
    inputfile = ''
    outputfile = ''
    parser = argparse.ArgumentParser(prog="CARMACloud Analysis for ERV BSM")
    parser.add_argument('--input', type=str, required=True)
    parser.add_argument('--output', type=str, required=True)
    args = parser.parse_args()
    print(f'Received log file: {args.input}; Result will be saved into file: {args.output}.xlsx')
    inputfile = args.input
    outputfile = args.output
    return (inputfile, outputfile) 

'''
Read the input logs file and search the relevant logs. Process the logs and return a dictionary of the relevant information
'''
def process_input_log_file(inputfile, search_keyword):
    fields_dict = {}
    fields_dict["Time (UTC)"] = []
    try:
        file_stream = open(inputfile, 'r')
        while True:
            line = file_stream.readline()
            # if line is empty, end of file is reached
            if not line:
                break
            if len(line.strip()) == 0:
                continue
            if len(line.strip().split(' - ')) < 2:
                print(f'SKIPPED line: The log line has no log content: {line}')
                continue
            txt = line.strip().split(' - ')[1]
            # Look for the specific metric by keyword
            if search_keyword.lower().strip() in txt.lower():
                metadata_list = [x for x in line.strip().split(
                    ' - ')[0].split(" ") if x != '']
                time = metadata_list[1]
                fields_dict["Time (UTC)"].append(time)
                metric_field_value = ''
                metric_field_title = ''
                txt_list = txt.split(";")
                for txt_item in txt_list:
                    if "Received: BSMRequest [v2xhub_port=" in txt_item:
                        txt_item_list = txt_item.strip().split(',')
                        for item in txt_item_list:
                            if "v2xhub_port=" in item:
                                v2xhub_port = item.split('v2xhub_port=')[1]
                                if 'v2xhub_port' not in fields_dict.keys():
                                    fields_dict['v2xhub_port'] = []
                                fields_dict["v2xhub_port"].append(v2xhub_port) 
                            elif "id=" in item:
                                bsm_req_id =  txt_item.strip().split(',')[1].split('id=')[1]
                                metric_field_title = 'Received: BSMRequest'
                                metric_field_value = bsm_req_id                                 
                    elif len(txt_item.strip().split(":")) == 2:
                        metric_field_title = txt_item.strip().split(":")[
                            0].strip().replace(" ", "_")
                        metric_field_value = txt_item.strip().split(":")[
                            1].strip()
                    elif len(txt_item.strip().split(":")) >= 3:
                        metric_title = txt_item.strip().split(":")[0].strip()
                        metric_field_title = txt_item.strip().split(":")[
                            1].strip().replace(" ", "_")
                        metric_field_value = txt_item.strip().split(":")[
                            2].strip().replace('to','').replace('V2xHub','').replace('port','')
                        
                        # Retrieve port number
                        if len(txt_item.strip().split(":")) == 4 and  ( "44444" in txt_item or  "44445" in txt_item or  "44446" in txt_item):
                            if 'port' not in fields_dict.keys():
                                fields_dict['port'] = []
                            fields_dict["port"].append(txt_item.strip().split(":")[
                            3].strip().replace('!',''))                    

                    if metric_field_title not in fields_dict.keys():
                        fields_dict[metric_field_title] = []

                    if "RSU_Names" in metric_field_title:
                        # remove random generated values from RSU names
                        tmp_list = metric_field_value.split(",")
                        tmp_list = [x for x in tmp_list if len(x) != 0]
                        tmp_field_value = ''
                        for x in tmp_list:
                            tmm_list = x.split('_')
                            tmm_list.pop()
                            tmp_field_value += '_'.join(tmm_list) + ","
                        metric_field_value = tmp_field_value

                    fields_dict[metric_field_title].append(metric_field_value)
        file_stream.close()
    except:
        print(f'Read file {inputfile} error.')
        exit()
    return fields_dict

'''
main entrypoint to read carmacloud.log file and search based on the metric keyworkds. 
Once the relevant logs are found, it write the data into the specified excel output file.
'''
def main():
    inputfile, outputfile = get_filenames()

    search_metric_keywords = {
        'FER-13-1-1':'Received: BSMRequest [v2xhub_port=',
        'FER-13-1':'FER-13-1',
        'FER-13-2': 'FER-13-2',
        'FER-14': 'FER-14',
        'FER-15':'FER-15',
        'FER-TBD-1': 'IGNORE already Processed BSM'
    }
    global_fields_dict = {}
    for metric_keyword_key in search_metric_keywords.keys():
        if len(inputfile) > 0 and len(outputfile) > 0:
            fields_dict = process_input_log_file(inputfile=inputfile,
                                   search_keyword=search_metric_keywords[metric_keyword_key])
            global_fields_dict[metric_keyword_key] = fields_dict
    # Write dictionary into excel file
    with pd.ExcelWriter(outputfile+".xlsx") as writer:
        for metric_keyword  in global_fields_dict.keys():
            data_frame = pd.DataFrame(global_fields_dict[metric_keyword])
            data_frame.to_excel(writer, sheet_name=metric_keyword, index=False)
            print(f'Generated sheet for metric: {metric_keyword}' )

if __name__ == '__main__':
    main()
