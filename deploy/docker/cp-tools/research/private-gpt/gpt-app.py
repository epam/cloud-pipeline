# Copyright 2023 EPAM Systems, Inc. (https://www.epam.com/)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

from dotenv import load_dotenv
from chromadb.config import Settings
from flask import Flask, request, Response
from langchain.chains import RetrievalQA
from langchain.embeddings import HuggingFaceEmbeddings
from langchain.callbacks.streaming_stdout import StreamingStdOutCallbackHandler
from langchain.vectorstores import Chroma
from langchain.llms import GPT4All, LlamaCpp
import os
import argparse
import queue
import threading

app = Flask(__name__)

if not load_dotenv():
    print("Could not load .env file or it is empty. Please check if it exists and is readable.")
    exit(1)

embeddings_model_name = os.environ.get("EMBEDDINGS_MODEL_NAME")
persist_directory = os.environ.get('PERSIST_DIRECTORY')

model_type = os.environ.get('MODEL_TYPE')
model_path = os.environ.get('MODEL_PATH')
model_n_ctx = os.environ.get('MODEL_N_CTX')
target_source_chunks = int(os.environ.get('TARGET_SOURCE_CHUNKS', 4))
n_gpu_layers = os.environ.get('N_GPU_LAYERS')
use_mlock = os.environ.get('USE_MLOCK')
n_batch = os.environ.get('N_BATCH') if os.environ.get('N_BATCH') is not None else 512


CHROMA_SETTINGS = Settings(
        chroma_db_impl='duckdb+parquet',
        persist_directory=persist_directory,
        anonymized_telemetry=False
)


class ThreadedGenerator:

    def __init__(self):
        self.queue = queue.Queue()

    def __iter__(self):
        return self

    def __next__(self):
        item = self.queue.get()
        if item is StopIteration:
            raise item
        return item

    def send(self, data):
        self.queue.put(data)

    def close(self):
        self.queue.put(StopIteration)


class StreamingGeneratorCallbackHandler(StreamingStdOutCallbackHandler):

    def __init__(self, gen):
        super().__init__()
        self.gen = gen

    def on_llm_new_token(self, token: str, **kwargs):
        self.gen.send(token)


embeddings = HuggingFaceEmbeddings(model_name=embeddings_model_name)
db = Chroma(persist_directory=persist_directory, embedding_function=embeddings, client_settings=CHROMA_SETTINGS)
retriever = db.as_retriever(search_kwargs={"k": target_source_chunks})


def get_answer(generator, content, qa):
    try:
        qa(content)
    finally:
        generator.close()


@app.route('/chat', methods=['POST'])
def chat():
    content = request.json['message']
    threaded_generator = ThreadedGenerator()
    callback_handler = StreamingGeneratorCallbackHandler(threaded_generator)
    if model_type == "LlamaCpp":
        llm = LlamaCpp(model_path=model_path, n_ctx=model_n_ctx, callbacks=[callback_handler], verbose=False,
                       n_gpu_layers=n_gpu_layers, use_mlock=use_mlock, top_p=0.9, n_batch=n_batch)
    elif model_type == "GPT4All":
        llm = GPT4All(model=model_path, n_ctx=model_n_ctx, backend='gptj', callbacks=[callback_handler],
                      verbose=False)
    else:
        raise Exception(
            f"Model type {model_type} is not supported. Please choose one of the following: LlamaCpp, GPT4All")
    qa = RetrievalQA.from_chain_type(llm=llm, chain_type="stuff", retriever=retriever,
                                     return_source_documents=False)
    threading.Thread(target=get_answer, args=(threaded_generator, content, qa)).start()
    return Response(threaded_generator, mimetype='text/plain')


def main():
    args = parse_arguments()
    app.run(host=args.host, port=args.port)


def parse_arguments():
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", default="5000")

    return parser.parse_args()


if __name__ == "__main__":
    main()
