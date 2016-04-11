CQSENT
====================

This is a fun project to get acquainted with real-time sentiment analysis on twitter streams ingested from Amazon Kinesis, which allows for a convenient way (via variety of connectors to different data-aggreation services such as GNIP) to collect and combine text data source from multiple social netweork sources. We use a Naive Bayes classifier to separate the incoming messages in real-time into positive and negative tweets. 

Requirenments
============

* sbt (simple build tools)
* Scala version 2.11.7
* A free mazon AWS account
* Apache Spark
* Python 2.7 

Quick start
============

* At the AWS console, create two Amazon Kinesis Streams called "TwitterStreamTime" and "TwitterStream". Create a new IAM Role for these streams and download the associated keypair mykeypair.pem file

* At the AWS console, create a new S3 bucket called "cqs3bucket".

* First, download Apache Spark from [https://spark.apache.org/downloads.html](https://spark.apache.org/downloads.html) and extract the folder. Navigate to subfolder ec2, and launch a spark cluster with the command

```bash
./spark-ec2 -k aws_username -i mykeypair.pem -s 1 launch my_spark_cluster --region=us-east-1
```
Replace aws_username with your credentials. Currently, Amazon Kinesis is only supported in the US, but it might soon become available at the European nodes as well. 

* Login to your EC2 instance using

```bash
./spark-ec2 -k aws_username -i mykeypair.pem login my_spark_cluster --region=us-east-1
```

* Install cqspark on your instance using the command

```bash
git clone http://github.com/auerl/cqsent.git
```

* Download a traning dataset using wget (e.g., [http://thinknook.com/wp-content/uploads/2012/09/Sentiment-Analysis-Dataset.zip](http://thinknook.com/wp-content/uploads/2012/09/Sentiment-Analysis-Dataset.zip) and convert it to the XML format used by our algorithm. See, e.g., a possible conversion script [csv2xml.py](csv2xml.py). After having moved the XML data to the folder `data` in your cqsent installation folder.


* On your Spark cluster export the environment variables

```bash
export AWS_SECRET_ACCESS_EY=...
export AWS_ACCESS_KEY_ID=...
```

* Install simple build tools on your cluster via

```bash
sudo yum install http://repo.scala-sbt.org/scalasbt/sbt-native-packages/org/scala-sbt/sbt/0.13.1/sbt.rpm
```

* At https://apps.twitter.com/ create a new Twitter app and the associated CONSUMER_KEY, CONSUMER_SECRET, ACCESS_TOKEN_KEY, ACCESS_TOKEN_SECRET. For testing (and when no account to a data aggregation service like GNIP is available) we suggest using the Twitter public API and our custom Amazon Kinesis connector tweet_producer.py, which you can run either on your local machine or another EC2 instance. It is contained in the cqsent installation folder `./tools/tweet_producer_*.py`. Enter both  your AWS credentials and your Twitter Credentials therein. tweet_producer.py will forward tweets to Amazon kinesis in a stripped JSON format. The twitter public API provides access to around 1% of the full twitter firehose stream (around 50 tweets per second). 

* Now you are ready to test the code (Check at the AWS console whether your Kinesis streams do indeed correctly ingest the twitter stream). Run it with

```bash
sbt run
```

The Naive Bayes classifier will be trained using the training dataset, and incoming tweets will be classified in either either "positive" or "negative" and results will be stored in raw format at your S3 bucket for further analysis. Feel free to try out our code for different purposes.