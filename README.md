Streaming HTTP file upload demo for Play 2.1
============================================

Large uploads, such as movies, should not be completely buffered by the Play app before copying the data to the destination.
The [Play 2.1 Iteratee examples](http://www.playframework.com/documentation/2.1.0/ScalaFileUpload) for doing file upload
do not explain how to use small in-memory buffers to stream an upload from the client to a destination.

This small demo shows how large files can be copied from client to a destination without requiring any temporary files,
and only requires a minimal memory footprint. This means that relatively few chunks would be held in memory for each
client performing a transfer.
One complication is that it is common for the chunk size sent from browser to a Play server to be small.
For example, Firefox sends 8KB chunks when uploading files.
However, content repositories such as AWS S3 and Azure, have a larger minimum chunk size.
Azure imposes a 1MB chunk minimum, and AWS S3 has a 5MB minimum, and also restricts file uploads to 10,000 chunks.
This means that AWS chunks must be larger than 5MB when uploading very large files.

This project demonstrates two types of streaming upload destinations:

 *  Streaming a file upload to a local file
 *  Streaming a file upload to AWS S3 using the [AWS Java SDK](http://aws.amazon.com/documentation/sdkforjava/).

Just clone the repo and run it with Play on your local machine:
<pre>play debug run</pre>
You'll find inline comments in the code.
You can also read [the StackOverflow post](http://stackoverflow.com/questions/11916911/play-2-x-reactive-file-upload-with-iteratees).

Running the demo app
--------------------
Before you can run the app and upload to AWS S3, you must set some environment variables before you run Play:
<pre>export awsAccessKey   = "yourAwsAccessKey"
export awsSecretKey   = "yourAwsSecretKey"
export awsBucketName  = "yourAwsBucketName"</pre>

