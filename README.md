Streaming HTTP file upload demo for Play 2.1
============================================

This small demo shows how large files can be copied from client to a destination without requiring any temporary files,
and only requires a minimal memory footprint. For example, Firefox sends 8KB chunks when uploading files, so only a
few chunks would be held in memory for each client performing a transfer.

Large uploads, such as movies, should not be completely buffered by the Play app before copying the data to the destination.
The [Play 2.1 Iteratee examples](http://www.playframework.com/documentation/2.1.0/ScalaFileUpload) for doing file upload
do not explain how to use small in-memory buffers to stream an upload from the client to a destination.

This project demonstrates two types of streaming upload destinations:

 *  Streaming a file upload to a local file
 *  Streaming a file upload to AWS S3 using the [AWS Java SDK](http://aws.amazon.com/documentation/sdkforjava/).
   To complicate matters, AWS S3 has a minimum chunk size of 5MB, and a maximum file size of 5TB.

Just clone the repo and run it with Play on your local machine:
<pre>play debug run</pre>
You'll find inline comments in the code.

AWS Configuration
-----------------
To run the app and upload to AWS S3, you must set some environment variables before you run Play:
<pre>export awsAccessKey   = "yourAwsAccessKey"
export awsSecretKey   = "yourAwsSecretKey"
export awsBucketName  = "yourAwsBucketName"</pre>

