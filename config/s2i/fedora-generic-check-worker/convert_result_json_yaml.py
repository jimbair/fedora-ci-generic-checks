# -*- coding: utf-8 -*-
# Copyright 2019, Red Hat, Inc.
# License: GPL-2.0+ <http://spdx.org/licenses/GPL-2.0+>

"""
This is a utility script written to translate the json output from rpminspect into the yaml
format specified by the standard test interface:
    https://docs.fedoraproject.org/en-US/ci/standard-test-interface/#_results_format
"""

import json
import argparse
import yaml

def get_argparser():
    parser = argparse.ArgumentParser(description="Transforms json from rpminspect to STR yaml format")
    parser.add_argument("jsonfile", help="JSON file to translate")
    parser.add_argument("-o", "--outputfile", default=None, help="filename to dump output to")

    return parser

def parse_json(jsonfilename):
    with open(jsonfilename, 'r') as jsonfile:
        return json.load(jsonfile)

def process_json(json_data):
    """Process json so that it can be emitted as yaml following the STI yaml format
    https://docs.fedoraproject.org/en-US/ci/standard-test-interface/#_results_format
    """

    results = []

    for testcase in json_data.keys():
        # for now, assuming that there can only be one result per test case, need to verify that
        # this is actually the case
        testcase_name = testcase.lower().replace(' ', '-')
        result_string = json_data[testcase][0]['result'].lower()

        # assuming that 'ok' and 'info' correlate to 'pass' and anything else is 'fail'
        result = {'test':'dist.rpminspect.{}'.format(testcase_name), 'result':'pass' if result_string in ['ok', 'info'] else 'fail'}
        results.append(result)

    return {"results": results}

def dump_yaml(data):
    return yaml.dump(data)

if __name__ == "__main__":
    parser = get_argparser()
    args = parser.parse_args()

    jsondump = parse_json(args.jsonfile)

    processed_json = process_json(jsondump)

    yamlout = dump_yaml(processed_json)

    if args.outputfile is None:
        print(yamlout)
    else:
        with open(args.outputfile, 'w') as outfile:
            outfile.write(yamlout)
