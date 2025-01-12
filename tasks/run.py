#!/usr/bin/env python

import simplejson as json
import sys
import os
import itertools
from collections import OrderedDict
import subprocess
import csv
import re
import math
import errno
import time

base_commands = {
    'vpr': [
        './vpr',
        '{architecture_file}',
        '{circuit}',
        '--blif_file', '{blif_file}',
        '--net_file', '{net_file}',
        '--place_file', '{place_file}',
        '--route_file', '{route_file}',
        '--fix_pins', 'random',
        '--place'
    ],
    'java': [
        'java',
        '-cp',
        'bin',
        '-Xmx30g',
        'interfaces.CLI',
        '{architecture_file}',
        '{blif_file}',
        '--net_file', '{net_file}',
        '--output_place_file', '{place_file}',
    ],
}



def silentremove(filename):
    # source: http://stackoverflow.com/a/10840586
    try:
        os.remove(filename)
    except OSError as e: # this would be "except OSError, e:" before Python 2.6
        if e.errno != errno.ENOENT: # errno.ENOENT = no such file or directory
            raise # re-raise exception if a different error occured


def ascii_encode(string):
    if isinstance(string, unicode):
        return string.encode('ascii')
    else:
        return string

def ascii_encode_ordered_dict(data):
    return OrderedDict(map(ascii_encode, pair) for pair in data)


def substitute(command, substitutions):
    new_command = []

    for i in range(len(command)):
        argument = command[i]
        for key, value in substitutions.items():
            argument = argument.replace('{' + key + '}', value)

        new_command.append(argument)

    return new_command


def argument_sets(argument_names, argument_values):
        for argument_value_set in itertools.product(*argument_values):
            argument_set = []
            for i in range(len(argument_names)):
                argument_set += [str(argument_names[i]), str(argument_value_set[i])]

            yield argument_set


def call(command, stats, regexes):
    process = subprocess.Popen(' '.join(command), stdout=subprocess.PIPE, stderr=subprocess.PIPE, shell=True)
    out, err = process.communicate()
    out = out.decode('utf-8')
    err = err.decode('utf-8')

    if err:
        print('        Error!')
        return out + '{0}\n\nThere was a problem with command "{1}":\n\n{2}'.format(out, ' '.join(command), err)

    for line in out.splitlines():
        for i in range(len(regexes)):
            match = re.search(regexes[i], line)
            if match:
                stats[i] = match.group(1)

    return 'Command: {0}\n\n{1}'.format(' '.join(command), out)


folder = os.path.join(os.path.split(os.getcwd())[1], sys.argv[1])
os.chdir('..')
subprocess.call(['./compile.sh'])

config_path = os.path.join(folder, 'config.json')
config_file = open(config_path)
config = json.load(config_file, object_pairs_hook=ascii_encode_ordered_dict)

# Get some config options
architecture_file = config['architecture']
blif_file = config['blif_file']
net_file = config['net_file']

placer = config['placer']

stats = config['stats']
stat_names = stats.keys()
stat_regexes = stats.values()

# Build the base command
base_command = base_commands[placer]
extra_command = []

# Set the router and statistics command (if needed)
call_router = config['route']
if call_router:
    stat_names += ['post-routing bb cost', 'post-routing max delay']
    stat_regexes += [
        'Total wirelength: ([0-9.e+-]+), average net length',
        'Final critical path: ([0-9.e+-]+) ns'
    ]

    if placer == 'java':
        extra_command += [';'] + base_commands['vpr'][:-3]

    extra_command += ['--route']

call_statistics = (placer == 'vpr')
if call_statistics:
    extra_command += [';'] + base_commands['java'][:-2] + ['--input_place_file', '{place_file}']


# Get the circuits
circuits = config['circuits']
if not isinstance(circuits, list):
    circuits = str(circuits).split(' ')


# Loop over the different combinations of arguments
arguments = config['arguments']
if type(arguments) is OrderedDict:
    argument_names = arguments.keys()
    argument_values = arguments.values()
else:
    argument_names = arguments[0]
    argument_values = arguments[1]

for i in range(len(argument_values)):
    if type(argument_values[i]) is not list:
        argument_values[i] = [argument_values[i]]

argument_sets = list(argument_sets(argument_names, argument_values))
num_iterations = len(argument_sets)

summary_file = open(os.path.join(folder, 'summary.csv'), 'w')
summary_writer = csv.writer(summary_file)
summary_writer.writerow(['run'] + argument_names + stat_names)

circuit_writers = {}
for circuit in circuits:
    circuit_file = open(os.path.join(folder, circuit + '.csv'), 'w')
    circuit_writer = csv.writer(circuit_file)
    circuit_writer.writerow(['run'] + argument_names + stat_names)
    circuit_writers[circuit] = circuit_writer

num_stats = len(stat_names)

for iteration in range(num_iterations):

    # Create the output folder and stats file
    iteration_name = 'arguments' + str(iteration)
    output_folder = os.path.join(folder, iteration_name)
    if not os.path.isdir(output_folder):
        os.mkdir(output_folder)

    stats_file = open(os.path.join(output_folder, 'stats.csv'), 'w')
    stats_writer = csv.writer(stats_file)
    stats_writer.writerow(['circuit'] + stat_names)
    geomeans = [1] * num_stats

    # Build the complete command (without circuit substituted)
    argument_set = argument_sets[iteration]
    place_file = os.path.join(output_folder, '{circuit}.place')
    route_file = os.path.join(output_folder, '{circuit}.route')
    command = substitute(base_command + argument_set + extra_command, {
        'architecture_file': architecture_file,
        'blif_file': blif_file,
        'net_file': net_file,
        'place_file': place_file,
        'route_file': route_file
    })

    print(time.strftime("%Y-%m-%d %H:%M:%S"))
    print('{0}: {1}'.format(iteration_name, ' '.join(argument_set)))


    # Loop over all circuits
    for circuit in circuits:
        print('    ' + circuit)

        # Call the placer and get statistics
        circuit_stats = ['0'] * num_stats
        circuit_command = substitute(command, {'circuit': circuit})
        out = call(circuit_command, circuit_stats, stat_regexes)

        silentremove(circuit + '.critical_path.out')
        silentremove(circuit + '.slack.out')
        silentremove('vpr_stdout.log')

        dump_file = open(os.path.join(output_folder, circuit + '.log'), 'w')
        dump_file.write(out)
        dump_file.close()


        for i in range(num_stats):
            geomeans[i] *= float(circuit_stats[i])

        stats_writer.writerow([circuit] + circuit_stats)
        circuit_writers[circuit].writerow([iteration_name] + argument_set[1::2] + circuit_stats)

    for i in range(num_stats):
        geomeans[i] = str(math.pow(geomeans[i], 1.0 / len(circuits)))

    stats_writer.writerow([])
    stats_writer.writerow(['geomeans'] + geomeans)
    summary_writer.writerow([iteration_name] + argument_set[1::2] + geomeans)

    empty_row = [''] * (num_stats + 1)
    stats_writer.writerow(empty_row + [' '.join(argument_set)])
    stats_writer.writerow(empty_row + [' '.join(command)])

    stats_file.close()


summary_file.close()
