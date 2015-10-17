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

# get the gensim functions
from gensim.models import doc2vec
# import support libs
import gzip
import nltk
import os
import string
import gc
import random

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
    doc2vec_model = doc2vec.Doc2Vec(size=int(param.size), window=int(param.window), min_count=int(param.min_count), dm=int(param.dm), sample=float(param.sample), negative=int(param.negative), workers=model_workers, alpha=0.025, min_alpha=0.025)
    # create some vars
    docs = []

    def process_line(num, line):
      global docs
      docs.append(doc2vec.LabeledSentence(words=nltk.tokenize(line), labels=["SENT_" + str(num)]))
      if (len(docs) > 100):
        doc2vec_model.build_vocab(docs)
        doc2vec_model.train(random.shuffle(docs))
        docs = []

    # load the wikipedia files line by line and add to training
    with iter in range(20):
      line_count = 0;
      print("Starting file iteration ('" + param.dataset + "')...")
      with open(param.dataset, 'r') as infile:
        for num, line in enumerate(infile):
          line_count = line_count + 1;
          if (line_count % 100000 == 0):
            #print("parsed lines: " + str(line_count) + " / Memory: " + str(int(os.popen('ps -p %d -o %s | tail -1' % (os.getpid(), "rss")).read()) / 1000) + " mB / Items: " + str(len(docs)) )
            print("parsed lines: " + str(line_count) + " / Items: " + str(len(docs)) )
            gc.collect()
          if(random.random() < 0.25):
            process_line(line)
      print("epoch complete (" + str(iter) + " of 10) ...")
      doc2vec_model.alpha -= 0.001  # decrease the learning rate
      doc2vec_model.min_alpha = model.alpha  # fix the learning rate, no decay


    # save the model in the new and the old (word2vec) binary format
    doc2vec_model.save(model_path+".sav")
    doc2vec_model.save_word2vec_format(model_path+".bin", binary=True)
    print("Finished training model '"+model_name+"' (completed "+str(count)+" of "+str(len(params))+")!")
    print("[save path: '" + model_path + "']")

# doc2vec_model
