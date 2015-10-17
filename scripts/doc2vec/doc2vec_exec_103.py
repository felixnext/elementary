# get the gensim functions
from gensim.models import doc2vec
import nltk
import string
import os

# Tdefines the global doc2vec model
doc2vec_model = None

# load the model from the given path
def load(model_path):
  global doc2vec_model
  if os.path.isfile(model_path):
    doc2vec_model = doc2vec.Doc2Vec.load(model_path)
    return True
  else:
    return False

# returns the most similar vector for the given word (along with)
def similarVector(word):
  global doc2vec_model
  #vectors = doc2vec_model.most_similar(positive=[word])
  vector = doc2vec_model.infer_vector([word])
  return vector

# calculate a vector for the given text
def similarVectorDoc(text):
  global doc2vec_model
  vector = doc2vec_model.infer_vector(nltk.word_tokenize(text))
  return vector

# checks if the given word is contained in the vocabulary
# DOES NOT WORK
def containsWords(word):
  global doc2vec_model
  # work-around: check if infered vector has high prob
  vectors = doc2vec_model.most_similar(positive=[word])
  return (vectors[0][1] > 0.99)

# returns the most similar words based on all positive and negative words given
def mostSimilar(pos, neg):
  global doc2vec_model
  vectors = doc2vec_model.most_similar(positive=pos, negative=neg)
  return vectors

# calculates the cosine similarity between two words
def cosineWords(w1, w2):
  global doc2vec_model
  sim = doc2vec_model.similarity(w1, w2)
  return sim
