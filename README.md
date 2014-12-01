cloudquake-sentiment
====================

A set of tools to do real-time sentiment analysis on twitter streams from Amazon Kinesis

Requirements
============

Download any traning dataset, such as http://thinknook.com/wp-content/uploads/2012/09/Sentiment-Analysis-Dataset.zip from the Internet and convert it using an adapted version of csv2xml.py. Put the xml files in the data folder. Requires simple build tools to be installed. Environment variables AWS_ACCESS_KEY and AWS_SECRET_KEY need to be set to connect to the Amazon Kinesis Stream


Running sentiment analysis
==========================

    $ sbt run    
    