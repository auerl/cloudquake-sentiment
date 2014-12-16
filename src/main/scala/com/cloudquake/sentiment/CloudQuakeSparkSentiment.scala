package com.cloudquake.sentiment

import java.io.{File, FilenameFilter}
import java.nio.ByteBuffer
import scala.util.Random
import jline.ConsoleReader

import org.apache.spark.mllib.classification.{NaiveBayes, NaiveBayesModel}
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.regression.LabeledPoint

import org.apache.spark.Logging
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.apache.spark.rdd.RDD
import org.apache.spark.storage.StorageLevel

import org.apache.spark.streaming.{Milliseconds,Seconds}
import org.apache.spark.streaming.StreamingContext
import org.apache.spark.streaming.StreamingContext.toPairDStreamFunctions
import org.apache.spark.streaming.kinesis.KinesisUtils

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.services.kinesis.AmazonKinesisClient
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.InitialPositionInStream
import com.amazonaws.services.kinesis.model.PutRecordRequest

import org.json4s._
import org.json4s.native.JsonMethods._
import org.apache.log4j.PropertyConfigurator


object CloudQuakeSparkSentiment extends App {


  /* Parameters for AWS and Naives Bayes text classifier */
  val batchInterval = Seconds(10) // checkpoint every ten seconds
  val countInterval = Seconds(60) // count feelings in the previous minute
  val streamName = "TwitterAmbiguous"
  val endpointUrl = "https://kinesis.us-east-1.amazonaws.com"
  val trainingDataDir = "data" 
       
  /* Configure output verbosity */
  PropertyConfigurator.configure("./log4j.properties")

  /* Setup Spark Configuration */
  val sparkConfig = new SparkConf().setAppName("cloudquake-sentiment")
                                   .set("spark.cleaner.ttl","7200")
				   .setMaster("local[4]")

  /* A function to parse the JSON formatted tweets */
  implicit val formats = DefaultFormats
  case class TweetContent(content: String) 
  def parse_content(json: String): String = {
                val parsedJson = parse(json)
                val m = parsedJson.extract[TweetContent]
  return m.content
  } 

  /* Determine the number of shards from the stream */
  val kinesisClient = new AmazonKinesisClient(new DefaultAWSCredentialsProviderChain()
                      .getCredentials())
  kinesisClient.setEndpoint(endpointUrl)
  val numShards = kinesisClient.describeStream(streamName)
                  .getStreamDescription().getShards().size()

  /* For now use 1 Kinesis Worker/Receiver/DStream for each shard. */
  val numStreams = numShards

  /* Start up spark streaming context */
  val ssc = new StreamingContext(sparkConfig, batchInterval)
  val sc  = new SparkContext(sparkConfig)


  /* Interact with Amazon S3 */
  val hadoopConf=sc.hadoopConfiguration;
  hadoopConf.set("fs.s3.impl", "org.apache.hadoop.fs.s3native.NativeS3FileSystem")
  val myAccessKey = sys.env("AWS_ACCESS_KEY_ID")
  val mySecretKey = sys.env("AWS_SECRET_KEY")
  hadoopConf.set("fs.s3.awsAccessKeyId",myAccessKey)
  hadoopConf.set("fs.s3.awsSecretAccessKey",mySecretKey)

  /* Set checkpoint directory */
  ssc.checkpoint("/root/check/")

  /* Kinesis checkpoint interval. Same as batchInterval for this example. */
  val kinesisCheckpointInterval = batchInterval
  
  /* Create the same number of Kinesis DStreams/Receivers as Kinesis stream's shards */
  val kinesisStreams = (0 until numStreams).map { i =>
      KinesisUtils.createStream(ssc, streamName, endpointUrl, kinesisCheckpointInterval,
      InitialPositionInStream.LATEST, StorageLevel.MEMORY_AND_DISK_2)
  }

  /* Union all the streams */
  val unionStreams = ssc.union(kinesisStreams)
  val tweets = unionStreams.flatMap(byteArray => new String(byteArray).split("\n"))     

  /* Create Naive Bayes model */
  val naiveBayesAndDictionaries = createNaiveBayesModel(trainingDataDir)

  /* Count positive and negative tweets in the batch Interval */
  val sentiments = tweets.map(parse_content).map(predict_sentiment(naiveBayesAndDictionaries,_))
  val count_sentiments = sentiments.map((_, 1)).reduceByKeyAndWindow(_ + _,countInterval)
                                   .map{case (sentiment, count) => (count, sentiment)}
                                   .transform(_.sortByKey(false))
  /* Print out the counts */				   
  count_sentiments.print()

  /* Save the the number of positive and negative tweets to S3 bucket*/
  count_sentiments.foreachRDD(rdd_sta => {
    if (rdd_sta.count > 0) {
    rdd_sta.saveAsTextFile("s3n://cqs3bucket/sentiment/no_"+System.currentTimeMillis)
    } 
  })
				     
  ssc.start()
  ssc.awaitTermination()
 


  /**
    *  Function to classify a tweet that has been parsed using json4s before
    */


  def predict_sentiment(naiveBayesAndDictionaries: NaiveBayesAndDictionaries, url: String): String = {
 
    // Tokenize and stem content of tweet
    val tokens = Tokenizer.tokenize(url)
    
    // compute TFIDF vector
    val tfIdfs = naiveBayesAndDictionaries.termDictionary.tfIdfs(tokens, naiveBayesAndDictionaries.idfs)
    val vector = naiveBayesAndDictionaries.termDictionary.vectorize(tfIdfs)
    val labelId = naiveBayesAndDictionaries.model.predict(vector)

    // convert label from double
    return naiveBayesAndDictionaries.labelDictionary.valueOf(labelId.toInt)

  }



  /**
   *  Creates a dictionary in terms of tf-idf values and trains the Naive Bayes classifier
   */
  def createNaiveBayesModel(directory: String) = {
    val inputFiles = new File(directory).list(new FilenameFilter {
      override def accept(dir: File, name: String) = name.endsWith(".xml")
    })

    val fullFileNames = inputFiles.map(directory + "/" + _)
    val docs = XmlParser.parseAll(fullFileNames)
    val termDocs = Tokenizer.tokenizeAll(docs)

    // put collection in Spark
    val termDocsRdd = sc.parallelize[TermDoc](termDocs.toSeq)
    val numDocs = termDocs.size

    // create dictionary term => id
    // and id => term
    val terms = termDocsRdd.flatMap(_.terms).distinct().collect().sortBy(identity)
    val termDict = new Dictionary(terms)

    val labels = termDocsRdd.flatMap(_.labels).distinct().collect()
    val labelDict = new Dictionary(labels)

    // compute TFIDF and generate vectors
    // for IDF
    val idfs = (termDocsRdd.flatMap(termDoc => termDoc.terms.map((termDoc.doc, _))).distinct().groupBy(_._2) collect {
      // mapValues not implemented :-(
      // if term is present in less than 3 documents then remove it
      case (term, docs) if docs.size > 3 =>
        term -> (numDocs.toDouble / docs.size.toDouble)
    }).collect.toMap

    val tfidfs = termDocsRdd flatMap {
      termDoc =>
        val termPairs = termDict.tfIdfs(termDoc.terms, idfs)
        // we consider here that a document only belongs to the first label
        termDoc.labels.headOption.map {
          label =>
            val labelId = labelDict.indexOf(label).toDouble
            val vector = Vectors.sparse(termDict.count, termPairs)
            LabeledPoint(labelId, vector)
        }
    }

    val model = NaiveBayes.train(tfidfs)
    NaiveBayesAndDictionaries(model, termDict, labelDict, idfs)
  }
}

case class NaiveBayesAndDictionaries(model: NaiveBayesModel,
                                     termDictionary: Dictionary,
                                     labelDictionary: Dictionary,
                                     idfs: Map[String, Double])
