#! /usr/bin/env python3
# Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import sys
import json
import urllib.parse, urllib.request


def parse_embedding(hit_json):
    embedding_json = hit_json["fields"]["embedding"]["cells"]
    embedding_vector = [0.0] * len(embedding_json)
    for val in embedding_json:
        embedding_vector[int(val["address"]["d0"])] = val["value"]
    return embedding_vector


def query_user_embedding(user_id):
    yql = 'select * from sources user where user_id contains "{}";'.format(user_id)
    url = "http://localhost:8080/search/?yql={}&hits=1".format(urllib.parse.quote_plus(yql))  # GET
    result = json.loads(urllib.request.urlopen(url).read())
    embedding = parse_embedding(result["root"]["children"][0])
    return embedding


def query_news(user_vector, hits, filter):
    nn_annotations = [
        '"targetHits":{}'.format(hits)
        ]
    nn_annotations = "{" + ",".join(nn_annotations) + "}"
    nn_search = "([{}]nearestNeighbor(embedding, user_embedding))".format(nn_annotations)

    data = {
        "hits": hits,
        "yql": 'select * from sources news where {} {};'.format(nn_search, filter),
        "ranking.features.query(user_embedding)": str(user_vector),
        "ranking.profile": "recommendation"
    }
    req = urllib.request.Request("http://localhost:8080/search/", data=str(data).encode('utf-8'))  # POST
    req.add_header('Content-Type', 'application/json')
    try:
        return json.loads(urllib.request.urlopen(req).read())
    except urllib.error.HTTPError as e:
        return json.loads(e.read())


def main():
    user_id = sys.argv[1]
    hits = sys.argv[2] if len(sys.argv) > 2 else 10
    filter = sys.argv[3] if len(sys.argv) > 3 else ""

    user_vector = query_user_embedding(user_id)
    result = query_news(user_vector, int(hits), filter)

    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()

