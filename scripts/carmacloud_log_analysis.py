import sys
import getopt
import pandas as pd

'''
Get file names
'''
def get_filenames(argv):
    inputfile = ''
    outputfile = ''
    try:
        if len(argv) < 4:
            print('python3 carmacloud_log_analysis.py -i <inputfile> -o <outputfile>')
        else:
            opts, args = getopt.getopt(args=argv, shortopts="i:s:o:")
            for opt, arg in opts:
                if opt == '-i':
                    inputfile = arg
                elif opt == "-o":
                    outputfile = arg
        return (inputfile,  outputfile)
    except:
        print('python3 carmacloud_log_analysis.py -i <inputfile> -o <outputfile>')
        sys.exit()

'''
Read the input logs file and search the relevant logs. Process the logs and return a dictionary of the relevant information
'''
def process_input_log_file(inputfile, search_keyword):
    file_stream = open(inputfile, 'r')
    fields_dict = {}
    fields_dict["Time"] = []
    while True:
        line = file_stream.readline()
        # if line is empty, end of file is reached
        if not line:
            break
        if len(line.strip()) == 0:
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
            count = 0
            for txt_item in txt_list:
                if count > 0:
                    metric_field_title = txt_item.strip().split(":")[
                        0].strip().replace(" ", "_")
                    metric_field_value = txt_item.strip().split(":")[
                        1].strip()
                else:
                    metric_title = txt_item.strip().split(":")[0].strip()
                    metric_field_title = txt_item.strip().split(":")[
                        1].strip().replace(" ", "_")
                    metric_field_value = txt_item.strip().split(":")[
                        2].strip()
                    count += 1

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
    return fields_dict

'''
main entrypoint to read carmacloud.log file and search based on the metric keyworkds. 
Once the relevant logs are found, it write the data into the specified excel output file.
'''
def main(argv):
    inputfile, outputfile = get_filenames(argv=argv)
    search_metric_keywords = ['FER-13-1','FER-13-2','FER-14','FER-15']
    global_fields_dict = {}
    for metric_keyword in search_metric_keywords:
        if len(inputfile) > 0 and len(outputfile) > 0:
            fields_dict = process_input_log_file(inputfile=inputfile,
                                   search_keyword=metric_keyword)
            global_fields_dict[metric_keyword] = fields_dict
            
    # Write dictionary into excel file
    with pd.ExcelWriter(outputfile+".xlsx") as writer:
        for metric_keyword  in global_fields_dict.keys():
            data_frame = pd.DataFrame(global_fields_dict[metric_keyword])
            data_frame.to_excel(writer, sheet_name=metric_keyword, index=False)
            print(f'Generated sheet for metric: {metric_keyword}' )

if __name__ == '__main__':
    main(sys.argv[1:])
