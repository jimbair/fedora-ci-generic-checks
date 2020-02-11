# -*- coding: utf-8 -*-
# Copyright 2019, Red Hat, Inc.
# License: GPL-2.0+ <http://spdx.org/licenses/GPL-2.0+>

"""
This is a utility script written to translate the json output from rpminspect into other formats.
The two output formats supported are:
    1. Human-understandable text to make the output easier to understand.
    3. YAML in the format specified by the standard test interface:
       https://docs.fedoraproject.org/en-US/ci/standard-test-interface/#_results_format
"""

import json
import argparse
import yaml

def get_argparser():
    parser_description ="Processes rpminspect json to human-understandable text or STI results YAML"
    parser = argparse.ArgumentParser(description=parser_description)
    parser.add_argument("jsonfile", help="JSON file to translate")
    parser.add_argument("-o", "--outputfile", default=None,
                        help="filename to dump output to, will output to stdout if no filename given")
    parser.add_argument("-t", "--type", default='human', choices=['yaml', 'human'],
                        help="Type of output to generate (default: human)")

    return parser

def parse_json(jsonfilename):
    with open(jsonfilename, 'r') as jsonfile:
        return json.load(jsonfile)

def process_json(json_data):
    """Process rpminspect json so that we can do more with it"""

    results = {'passed': [], 'not-passed': []}

    for testcase in json_data.keys():

        testcase_name = testcase.lower().replace(' ', '-')
        testcase_items = []

        # keep track of the overall pass/fail based on the items within the testcase
        overall_pass = True

        for testcase_item in json_data[testcase]:
            result_string = testcase_item['result'].lower()

            # right now, operating off the belief that anything not OK or INFO is a fail
            testcase_result = result_string in ['ok', 'info']

            if not testcase_result:
                overall_pass = False

            # not all items have a message
            if 'message' in testcase_item.keys():
                result_message = testcase_item['message']
            else:
                result_message = ''

            # only some items have a remedy
            if 'remedy' in testcase_item.keys():
                result_remedy = testcase_item['remedy']
            else:
                result_remedy = ''

            testcase_items.append({'result': result_string,
                                   'passed': testcase_result,
                                   'message': result_message,
                                   'remedy': result_remedy})

        testcase_details = {'name': testcase_name, 'passed': overall_pass, 'items': testcase_items}

        if overall_pass:
            results['passed'].append(testcase_details)
        else:
            results['not-passed'].append(testcase_details)

    overall_pass = 'PASSED' if len(results['not-passed']) == 0 else 'NOT PASSED'
    results['status'] = overall_pass

    return results

def generate_yaml_dict(data):
    """reformat the parsed json so that it adheres to the STI results YAML format:
        https://docs.fedoraproject.org/en-US/ci/standard-test-interface/#_results_format
    """

    output = []

    # generate dict for yaml
    for fail_case in data['not-passed']:
        output.append({'result': 'FAIL', 'test': fail_case['name'], 'logs': []})
    for pass_case in data['passed']:
        output.append({'result': 'PASS', 'test': pass_case['name'], 'logs': []})

    sorted_output = sorted(output, key=lambda result: result['test'])

    return {"results": output}


def generate_output(data):
    output = []
    output.append('rpminspect output for humans - see rpminspect.json for full details')
    output.append('Overall Result: {}'.format(data['status']))
    output.append('NOT PASSED items:')
    for fail_result in data['not-passed']:
        output.append('  {}:'.format(fail_result['name']))
        for testcase_item in fail_result['items']:
            output.append('    message: {}'.format(testcase_item['message']))
            output.append('    remedy: {}'.format(testcase_item['remedy']))
            output.append('')

    output.append('PASSED items:')
    for pass_result in data['passed']:
        output.append('  {}'.format(pass_result['name']))

    return output

if __name__ == "__main__":
    parser = get_argparser()
    args = parser.parse_args()

    jsondump = parse_json(args.jsonfile)

    processed_json = process_json(jsondump)

    if args.type == 'yaml':
        yaml_dict = generate_yaml_dict(processed_json)
        output = yaml.dump(yaml_dict)
    else:
        output_dict = generate_output(processed_json)
        output = '\n'.join(output_dict)

    if args.outputfile is None:
        print(output)
    else:
        with open(args.outputfile, 'w') as outfile:
            outfile.write(output)
