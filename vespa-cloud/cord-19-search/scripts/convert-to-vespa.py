#!/usr/bin/env python3
# Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import pandas
import sys
import json
import datetime
from os import path
from sentence_transformers import SentenceTransformer
import numpy as np

def long_text_parse(name,md):
  if md.get(name) == None:
    return ('',{})
  paragraphs = []
  sections = {}
  all = []
  for p in md.get(name):
    section = p.get('section')
    text = p.get('text')
    all.append(text)
    section = section.lower()
    if sections.get(section) == None:
      sections[section] = [text]
    else:
      sections[section].append(text)
  return (' '.join(all), sections)
     

def parse_file(dir,sha):
  file = path.join(DATA_DIR, dir, str(sha) + '.json')
  if not path.exists(file):
    return ([], {}, {}, [],'','')
  with open(file, 'r') as f:
    data = json.load(f)
    md = data['metadata'] 
    authors = []
    for a in md.get('authors'):
      
      author = {
        'first' : a['first'] if a['first'] else None,
        'last': a['last'] if a['last'] else None,
        'middle': ' '.join(a['middle']) if ' '.join(a['middle']) else None,
        'suffix': a['suffix'] if a['suffix'] else None,
        'email': a['email'] if a['email'] else None
      }
      authors.append(author)
    abstract, abstract_paragraphs = long_text_parse('abstract',data) 
    body, body_paragraphs = long_text_parse('body_text',data) 
    bib_entries = []
    if data.get('bib_entries') != None: 
      bib_entries = data.get('bib_entries')
      bib_entries_array = []
      for b in bib_entries.keys():
        year = bib_entries[b]['year'] 
        try:
          year = int(year)
        except:
          year = 1900
        entry = {
          'ref_id' : bib_entries[b]['ref_id'], 
          'title' : bib_entries[b]['title'], 
          'year' : year 
        }
        bib_entries_array.append(entry)
        
    return (authors, abstract_paragraphs, body_paragraphs, bib_entries_array, abstract, body)

def get(df_row, key, defaultValue):
  value = df_row[key] 
  if value == 'notvalid':
    return defaultValue
  else:
    return value
  

def produce_vespa_json(idx, row):
  title = get(row,'title',None)
  abstract = get(row,'abstract',None)
  sha = row['sha']
  source = row['source_x']
  full_text_dir = row['full_text_file']
  license = get(row, 'license', None)
  journal = get(row, 'journal', None)
  pmcid = get(row, 'pmcid',None)
  pubmed_id  = get(row, 'pubmed_id',None)
  if pubmed_id != None:
    try:
      pubmed_id = int(pubmed_id)
    except:
      pass
  who_covidence = get(row, 'WHO #Covidence', None) 
  publish_time = get(row, 'publish_time', None)
  timestamp = 0
  try:
    timestamp = int(datetime.datetime.strptime(publish_time, '%Y-%m-%d').timestamp())
  except:
    pass
  doi = get(row, 'doi', None)
  has_full_text = row['has_full_text']
  if has_full_text:
    authors, abstract_paragraphs, body_paragraphs, bib_entries, abstract, body = parse_file(full_text_dir,sha)
  else:
    authors, abstract_paragraphs, body_paragraphs, bib_entries,body = ([], {}, {}, [],'')

  conclusion = ' '.join(body_paragraphs.get('conclusions',[]))
  results = ' '.join(body_paragraphs.get('results',[]))
  discussion = ' '.join(body_paragraphs.get('discussion',[]))
  methods = ' '.join(body_paragraphs.get('methods',[]))
  background = ' '.join(body_paragraphs.get('background',[]))
  introduction = ' '.join(body_paragraphs.get('introduction',[]))

  conclusion = conclusion if conclusion else None
  results = results if results else None
  discussion = discussion if discussion else None
  methods = methods if methods else None
  background = background if background else None
  introduction = introduction if introduction else None
  vespa_doc = {
    'put': 'id:covid-19:doc::%s' % idx, 
    'fields': {
      'title': title,
      'id': idx, 
      'source': source,
      'license': license,
      'datestring': publish_time,
      'doi': 'https://doi.org/%s' % doi, 
      'authors': authors,
      'bib_entries': bib_entries,
      'abstract': abstract,
      'journal': journal,
      'body_text' : body,
      'conclusion': conclusion,
      'introduction': introduction,
      'results': results,
      'discussion': discussion,
      'methods': methods,
      'background': background,
      'timestamp': timestamp,
      'pmcid': pmcid,
      'pubmed_id': pubmed_id,
      'who_covidence': who_covidence,
      'has_full_text': has_full_text,
      'abstract_embedding': { 'values':None},
      'title_embedding': { 'values':None},
      'cited_by': []
    } 
  }
  if abstract:
    abstract_embedding = model.encode([abstract])[0]
    vespa_doc['fields']['abstract_embedding']['values'] = abstract_embedding.tolist()
  else:
    vespa_doc['fields'].pop('abstract_embedding', None)

  if title:
    title_embedding = model.encode([title])[0]
    vespa_doc['fields']['title_embedding']['values'] = title_embedding.tolist()
  else:
    vespa_doc['fields'].pop('title_embedding', None)

  return vespa_doc

META_FILE = sys.argv[1]
SCIBERT_MODEL = sys.argv[2]
DATA_DIR = sys.argv[3]

model = SentenceTransformer(SCIBERT_MODEL) 

df = pandas.read_csv(META_FILE)
df = df.fillna("notvalid")
docs = {}
for idx, row in df.iterrows():
  doc = produce_vespa_json(idx,row)
  docs[doc['fields']['title']] = doc

for title in docs.keys():
  doc = docs[title]
  for ref in doc['fields']['bib_entries']:
    if ref['title'] in docs.keys():
      docs[ref['title']]['fields']['cited_by'].append(doc['fields']['id'])

for doc in docs.values():
  print(json.dumps(doc))




