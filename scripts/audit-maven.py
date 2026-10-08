#!/usr/bin/env python3
"""Audit the resolved runtime graph against OSV; service failure fails the check."""
import json
import re
import sys
import urllib.request
from pathlib import Path

report = Path(sys.argv[1]).read_text()
packages = set()
for group, name, version, selected in re.findall(
    r'(?:\+---|\\---) ([\w.\-]+):([\w.\-]+):([^\s]+)(?: -> ([^\s]+))?', report
):
    version = selected or version
    if ':' in version or version in {'FAILED', '(n)'}:
        raise ValueError('Unresolved runtime dependency')
    packages.add((f'{group}:{name}', version))
if not packages or 'FAILED' in report:
    raise ValueError('Runtime graph is empty or unresolved')
queries = [{'package': {'ecosystem': 'Maven', 'name': name}, 'version': version}
           for name, version in sorted(packages)]
request = urllib.request.Request('https://api.osv.dev/v1/querybatch',
    data=json.dumps({'queries': queries}).encode(),
    headers={'Content-Type': 'application/json'})
with urllib.request.urlopen(request, timeout=60) as response:
    results = json.load(response)['results']
if len(results) != len(queries):
    raise ValueError('Incomplete advisory response')
findings = [{'package': query['package']['name'], 'version': query['version'],
             'advisories': [v['id'] for v in result.get('vulns', [])]}
            for query, result in zip(queries, results) if result.get('vulns')]
output = {'checked': len(queries), 'source': 'OSV Maven database', 'findings': findings}
Path('maven-audit.json').write_text(json.dumps(output, indent=2) + '\n')
print(json.dumps(output))
sys.exit(bool(findings))
