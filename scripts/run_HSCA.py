import os, sys
import formatencoding
import uuid
import secrets

def generate_random_hex(n):
    return secrets.token_hex(n)

if __name__ == "__main__":

    argv_num = len(sys.argv)

    if argv_num < 4:
        print("argv_num Error !")
        exit(0)

    model_path = sys.argv[1]
    constr_path = sys.argv[2]
    cnf_path = f"tmp/{secrets.token_hex(32)}.cnf"
    group_path = f"tmp/{secrets.token_hex(32)}.txt"
    output_path = sys.argv[3]

    with open(model_path, "r") as modelFile:
        strength = int(modelFile.readline().strip())

    command = "cd format_converter && "
    command += f"python3 format_converter.py {model_path} {constr_path} ../{cnf_path} ../{group_path}"
    command += "&& cd .."
    os.system(command)

    seed = 1

    use_weight = 1
    L = 5000
    thread_num = 32
    choices_num = 100
    cutoff_time = 1000

    use_group = 1

    candidate_set_size = 100

    i = 4
    while i < argv_num:
        if sys.argv[i] == "-seed":
            seed = int(sys.argv[i + 1])
            i += 2
        elif sys.argv[i] == "-use_priority":
            use_weight = int(sys.argv[i + 1])
            i += 2
        elif sys.argv[i] == "-L":
            L = int(sys.argv[i + 1])
            i += 2
        elif sys.argv[i] == "-cutoff_time":
            cutoff_time = int(sys.argv[i + 1])
            i += 2
        elif sys.argv[i] == "-use_group":
            use_group = int(sys.argv[i + 1])
            i += 2
        else:
            print("argv Error !")
            i = argv_num
    
    boolean_CA_path = output_path + "_boolean_CA.out"

    command = f"./SamplingCA -seed {seed} -input_cnf_path {cnf_path} -output_testcase_path {boolean_CA_path}"
    os.system(command)

    command = f"./Generator -seed {seed} -input_cnf_path {cnf_path} -init_CA_file_path {boolean_CA_path} "
    command += f"-output_testcase_path {boolean_CA_path} -strength {strength} -group_file_path {group_path} "
    command += f"-L {L} -opt_method 1 -use_weight {use_weight} -use_group {use_group} -thread_num {thread_num} "
    command += f"-candidate_set_size {candidate_set_size}"
    os.system(command)

    if use_group == 0:
        ranName = uuid.uuid4()
        tmp_model_path = f"tmp/{ranName}.model"
        tmp_constr_path = f"tmp/{ranName}.constraints"
        formatEncoder = formatencoding.FormatEncoder(cnf_path, strength, tmp_model_path, tmp_constr_path)
        formatEncoder.encoding()
        command = f"./Optimizer {tmp_model_path} {tmp_constr_path} {cutoff_time} {seed} {boolean_CA_path} {output_path} {use_weight} {thread_num} {choices_num}"
        os.system(command)
        formatEncoder.clear()

    else:
        tmp_path = f"tmp/tmpfile_{generate_random_hex(32)}.out"
        command = f"python3 format_converter/turnCA.py {cnf_path} {model_path} {boolean_CA_path} {tmp_path}"
        os.system(command)

        if os.path.exists(cnf_path):
            os.remove(cnf_path)
        if os.path.exists(group_path):
            os.remove(group_path)
        if os.path.exists(boolean_CA_path):
            os.remove(boolean_CA_path)

        command = f"./Optimizer {model_path} {constr_path} {cutoff_time} {seed} {tmp_path} {output_path} {use_weight} {thread_num} {choices_num}"    
        os.system(command)

        if os.path.exists(tmp_path):
            os.remove(tmp_path)
