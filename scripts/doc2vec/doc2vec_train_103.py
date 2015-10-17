# load configuration
from pyhocon import ConfigFactory

config = ConfigFactory.parse_file('./application.conf')
param_config = config.get_string('doc2vec.config_file')

# load the trainings parameters
model_workers = config.get_int('doc2vec.model_workers')
model_cache_dir = config.get_string('doc2vec.cache_dir')

# class to contain all useful parameters for model training
class ModelParams:
  def __init__(self, name, datafile, size, window, min_count, negative, dm, dbow_words, sample):
    # holds the name of the dataset (e.g. en-wiki)
    self.dataname = name
    # holds the actual path to the dataset
    self.dataset = datafile
    # holds paramters for the model training
    self.size = size
    self.window = window
    self.min_count = min_count
    self.negative = negative
    self.dbow_words = dbow_words
    self.sample = sample
    self.dm = dm

  def toString(self):
    return "ModelParam(name="+str(self.dataname)+", file="+str(self.dataset)+", size="+str(self.size)+", window="+str(self.window)+", min_count="+str(self.min_count)+", negative="+str(self.negative)+", dbow="+str(self.dbow_words)+", sample="+str(self.sample)+", dm="+str(self.dm)+")"

# read all training data from file
params = []
with open(param_config) as configdata:
  for line in configdata:
    # parse (split according to csv)
    items = line.split(';')
    param = ModelParams(items[0], items[1], items[2], items[3], items[4], items[5], items[6], items[7], items[8])
    # add and give confirmation
    params.append(param)
    print("Found config: " + str(param.toString()) )

import pkg_resources
print("Gensim: " + str(pkg_resources.get_distribution("gensim").version))

# get the gensim functions
from gensim.models import doc2vec
# import support libs
import gzip
import nltk
import os
import string
import gc

# docs are holds globally to avoid recompute
docs = []
last_file = ""

# iterate through all configurations
for count, param in enumerate(params):
  # set name and path for resulting model
  model_name = "doc2vec-103_"+str(param.dataname)+"-sze_"+str(param.size)+"-wnd_"+str(param.window)+"-mc_"+str(param.min_count)+"-wrks_"+str(model_workers)+"-negative_"+str(param.negative)+"-dm_"+str(param.dm)+"-smpl_"+str(param.sample)+"-dbow_"+str(param.dbow_words)
  model_path = model_cache_dir+"/"+model_name
  doc2vec_model = None
  print("Train Model: " + model_name)

  # check if the model already exists
  if os.path.isfile(model_path):
    doc2vec_model = doc2vec.Doc2Vec.load(model_path)
    print("Model '"+model_name+"' already trained!")
  else:
    # create the model (continous training)
    #doc2vec_model = doc2vec.Doc2Vec(size=int(param.size), window=int(param.window), min_count=int(param.min_count), dm=int(param.dm), sample=param.sample, negative=int(param.negative), workers=model_workers)
    label_count = 1
    if(last_file != param.dataset):
      docs = []
      last_file = param.dataset
      gc.collect()
    word_buf = []

    def process_line(line):
      global word_buf
      global label_count
      # split into words and add them
      #words = nltk.word_tokenize(line)
      words = [word.strip(string.punctuation) for word in line.split()]
      for word in words:
        if (len(word) > 0):
          word_buf.append(word)
          # check if limit reached
          if(len(word_buf) >= 100):
            docs.append(doc2vec.LabeledSentence(words=word_buf, labels=["SENT_" + str(label_count)]))
            label_count = label_count + 1
            # define the overlap
            #word_buf = word_buf[10:]
            #word_buf = word_buf[75:]
            #word_buf = word_buf[90:]
            word_buf = []

    # load the wikipedia files line by line and add to training
    if (len(docs) == 0):
      line_count = 0;
      print("Starting file iteration ('" + param.dataset + "')...")
      with open(param.dataset, 'r') as infile:
        for line in infile:
          line_count = line_count + 1;
          if (line_count % 100000 == 0):
            #print("parsed lines: " + str(line_count) + " / Memory: " + str(int(os.popen('ps -p %d -o %s | tail -1' % (os.getpid(), "rss")).read()) / 1000) + " mB / Items: " + str(len(docs)) )
            print("parsed lines: " + str(line_count) + " / Items: " + str(len(docs)) )
            gc.collect()
          process_line(line)
      # train the final sequence of words (if any)
      if(len(word_buf) > 0):
        docs.append(doc2vec.LabeledSentence(words=sent, labels=["SENT_" + str(label_count)]))
      #if(len(docs) > 0):
      #  doc2vec_model.train(docs)
      print("loading complete (Items: " + str(len(docs)) + ") / training ...")

    # train the model
    doc2vec_model = doc2vec.Doc2Vec(docs, size=int(param.size), window=int(param.window), min_count=int(param.min_count), dm=int(param.dm), sample=float(param.sample), negative=int(param.negative), workers=model_workers)

    # save the model in the new and the old (word2vec) binary format
    doc2vec_model.save(model_path+".sav")
    doc2vec_model.save_word2vec_format(model_path+".bin", binary=True)
    print("Finished training model '"+model_name+"' (completed "+str(count)+" of "+str(len(params))+")!")
    print("[save path: '" + model_path + "']")

# doc2vec_model
