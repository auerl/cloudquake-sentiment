cqsent
====================

This is a fun project to get acquainted with real-time sentiment analysis on twitter streams ingested from Amazon Kinesis.

Requirenments
============

sbt
scala version

Quick start
============

Download an arbitrary traning dataset, such as http://thinknook.com/wp-content/uploads/2012/09/Sentiment-Analysis-Dataset.zip and convert it to the xml-type format used by our algorithm (see, e.g., the attached conversion tool csv2xml.py). Put the xml files in the data folder. 


Requires simple build tools to be installed. Environment variables AWS_ACCESS_KEY and AWS_SECRET_KEY need to be set to connect to the Amazon Kinesis Stream


Running sentiment analysis
==========================

    $ sbt run    
    
