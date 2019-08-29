# Elementary

Elementary is a question answering system based on information retrieval research to answer non-factoid questions from unstructured lecture transcripted.

## Getting Started

This project is rather old, so I haven't touched it in a while.

In general `sbt` should do most of the heavily lifting.

> TODO: I will try to come back to the project later to write a more concise setup note.

## Architecture

The architecture is based around scalable micro-services, which work through reactive-streams.

The actual retrieval process is based around document retrieval through Doc2Vec (trained on wikipedia data) and text understanding through entity recognition and Paragraph Embeddings.

## License

Published under MIT License.
