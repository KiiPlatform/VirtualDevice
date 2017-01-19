#!/usr/bin/env python
# -*- coding: utf-8 -*-

import json
import argparse
import requests
import datetime

#BASE_URL = 'https://qa21.internal.kii.com'
TIF_ENDPOINT = '/thing-if/'
UFE_ENDPOINT = '/api/'

FILE_EXT = '.json'

admin_token = None

def init_app():
	started_at = datetime.datetime.now()
	print '* Initializing app...\n'
	get_admin_token(args.client_id, args.client_secret)

	build_config()

	print '\nGenerating trait config files...'
	build_trait_definition('Lamp-V1', 1)
	build_trait_definition('EnvironmentSensor-V1', 1)

	print '\n* Initialization completed in:', datetime.datetime.now() - started_at

def get_admin_token(client_id, client_secret):
	url = get_ufe_url('oauth2/token')
	headers = {'Content-Type' : 'application/vnd.kii.OauthTokenRequest+json'}
	body = {
		'client_id' : client_id,
    	'client_secret' : client_secret,
    	'grant_type' : 'client_credentials'
	}

	req = requests.post(url, data=json.dumps(body), headers=headers)
	global admin_token
	admin_token = req.json()['access_token']

def build_trait_definition(trait_name, trait_version):
	url = get_thing_if_url('traits/%s/versions/%d' % (trait_name, trait_version))
	headers = {'Authorization' : 'Bearer %s' % admin_token}

	req = requests.get(url, headers=headers)

	definition = {}
	definition['actions'] = req.json()['actions']
	definition['states'] = req.json()['states']
	definition['dataGroupingInterval'] = req.json()['dataGroupingInterval']

	#print log_json(definition)

	save_config(definition, trait_name)

def build_config():
	print 'Generating config file...'
	data = {}
	data['kiiAppKey'] = args.app_key
	data['kiiSite'] = args.base_url
	data['kiiAppId'] = args.app_id

	supported_types = []

	thing_types = get_thing_types()
	for tt in thing_types:
		fw_versions = get_firmware_versions(tt)

		supported_type = {}
		supported_type['thingType'] = tt
		
		for fw in fw_versions:
			supported_type['firmwareVersion'] = fw

			aliases = get_aliases(tt, fw)
			
			supported_type['alias'] = []
			supported_type['alias'].append(aliases)

			supported_types.append(supported_type)

	data['SupportedTypes'] = supported_types

	save_config(data, 'config')

def get_thing_types():
	url = get_ufe_url('configuration/thing-types')
	headers = {'Authorization' : 'Bearer %s' % admin_token,
				'Content-Type' : 'application/vnd.kii.ThingTypesRetrievalResponse+json'}

	req = requests.get(url, headers=headers)

	thing_types = []

	for tt in req.json():
		thing_types.append(tt['thingType'])

	return thing_types

def get_firmware_versions(thing_type):
	url = get_ufe_url('configuration/thing-types/%s/firmware-versions' % thing_type)
	headers = {'Authorization' : 'Bearer %s' % admin_token}

	req = requests.get(url, headers=headers)

	firmware_versions = []

	for fw in req.json()['results']:
		firmware_versions.append(fw['firmwareVersion'])

	return firmware_versions

def get_aliases(thing_type, firmware_version):
	url = get_thing_if_url('/configuration/thing-types/%s/firmware-versions/%s/aliases' % (thing_type, firmware_version))
	headers = {'Authorization' : 'Bearer %s' % admin_token}

	req = requests.get(url, headers=headers)

	aliases = {}

	for alias in req.json()['aliases']:
		aliases['trait'] = alias['trait']
		aliases['name'] = alias['alias']

	return aliases

def log_json(data):
	print json.dumps(data, indent=2)

def get_thing_if_url(path):
	return '%sapps/%s/%s' % (args.base_url + TIF_ENDPOINT, args.app_id, path)

def get_ufe_url(path):
	return '%sapps/%s/%s' % (args.base_url + UFE_ENDPOINT, args.app_id, path)

def save_config(data, name):
	file_name = name + FILE_EXT
	with open(file_name, 'w') as f:
		try:
			json.dump(data, f, indent=2)
			print '>>> Config file %s was generated successfully' % name
		except Exception as e:
			print '¡!> Error saving config for %s: %s' % (name, e)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='VirtualDevice config initializer')
    parser.add_argument('-u', '--url', dest='base_url')
    parser.add_argument('-ai', '--app_id', dest='app_id')
    parser.add_argument('-ak', '--app_key', dest='app_key')
    parser.add_argument('-ci', '--client_id', dest='client_id')
    parser.add_argument('-cs', '--client_secret', dest='client_secret')
    
    args = parser.parse_args()

    if (args.base_url is None or args.app_id is None or args.app_key is None or 
    	args.client_id is None or args.client_secret is None):
        parser.print_help()
    else:
        init_app()
