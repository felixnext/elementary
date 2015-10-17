package elementary.tools.baseimporter

import com.typesafe.config.ConfigFactory
import org.deeplearning4j.models.word2vec._

object W2VTrainer {
/*
log.info("Load data....");
File file = new File("wikitext.txt");
SentenceIterator iter = new FileSentenceIterator(file);

TokenizerFactory t = new DefaultTokenizerFactory();

int layerSize = 300;

Word2Vec vec = new Word2Vec.Builder().sampling(1e-5)
.minWordFrequency(5).batchSize(1000).useAdaGrad(false)
.layerSize(layerSize).iterations(3).learningRate(0.025)
.minLearningRate(1e-2).negativeSample(10).iterate(iter)
.tokenizerFactory(t).build();
vec.fit();


        Nd4j.ENFORCE_NUMERICAL_STABILITY = true;

SerializationUtils.saveObject(vec,new File("w2v_model.ser"));
WordVectorSerializer.writeWordVectors(vec,"w2v_vectors.txt");
*/
}
