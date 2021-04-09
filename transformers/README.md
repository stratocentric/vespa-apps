<!-- Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root. -->

# Vespa sample application - Transformers

This sample application is a small example of using Transformers for ranking
using a small sample from the MS MARCO data set.

**Clone the sample:**

<pre data-test="exec">
$ git clone --depth 1 https://github.com/vespa-engine/sample-apps.git
$ APP_DIR=`pwd`/sample-apps/transformers
$ cd $APP_DIR
</pre>

**Install required packages**:

<pre data-test="exec">
$ pip3 install -qqq --upgrade pip
$ pip3 install -qqq torch transformers onnx onnxruntime
</pre>

**Set up the application package**:

This downloads the transformer model, converts it to an ONNX model and puts it
in the `files` directory. For this sample application, we use a standard
BERT-base model (12 layers, 110 million parameters), however other
[Transformers models](https://huggingface.co/transformers/index.html) can be
used. To export other models, for instance DistilBERT or ALBERT, change the
code in "src/python/setup-model.py". However, this sample application
contains a `WordPiece` tokenizer, so if the Transformer model requires a
different tokenizer, you would have to add that yourself.

<pre data-test="exec">
$ ./bin/setup-ranking-model.sh
</pre>

**Build the application package:**

This sample application contains a Java `WordPiece` tokenizer which is
invoked during document feeding and query handling. This compiles and
packages the Vespa application:

<pre data-test="exec">
$ mvn clean package
</pre>

**Create data feed:**

Convert from MS MARCO to a Vespa feed. Here we extract from sample data.
To use the entire MS MARCO data set, use the download script.

<pre data-test="exec">
$ ./bin/convert-msmarco.sh
</pre>

**Start Vespa:**

<pre data-test="exec">
$ docker run --detach --name vespa --hostname vespa-container --privileged \
  --volume $APP_DIR:/app --publish 8080:8080 vespaengine/vespa
</pre>

**Wait for the configserver to start:**

<pre data-test="exec" data-test-wait-for="200 OK">
$ docker exec vespa bash -c 'curl -s --head http://localhost:19071/ApplicationStatus'
</pre>

**Deploy the application:**

<pre data-test="exec">
$ docker exec vespa bash -c '/opt/vespa/bin/vespa-deploy prepare /app/target/application.zip && \
    /opt/vespa/bin/vespa-deploy activate'
</pre>

**Wait for the application to start:**

<pre data-test="exec" data-test-wait-for="200 OK">
$ curl -s --head http://localhost:8080/ApplicationStatus
</pre>

**Feed data:**

<pre data-test="exec">
$ docker exec vespa bash -c 'java -jar /opt/vespa/lib/jars/vespa-http-client-jar-with-dependencies.jar \
    --file /app/msmarco/vespa.json --host localhost --port 8080'
</pre>

**Test the application:**

This script reads from the MS MARCO queries and issues a
query to Vespa:

<pre data-test="exec" data-test-assert-contains="children">
$ ./src/python/evaluate.py
</pre>

**Shutdown and remove the container:**

<pre data-test="after">
$ docker rm -f vespa
</pre>

