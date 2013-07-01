Streaming HTTP file upload demo for Play 2.1 using AWS S3
=========================================================

UPDATED: This fork is an example for uploading to S3 using streaming / multipart upload. I have also updated the Scala AWS library
[https://github.com/mashhoodr/play-libraries].

Things to improve:
- The library is built using Futures but due to my limited knowledge I have not made the uploading of the parts
async since we need to make sure all the parts have uploaded before we can call the complete method. I shall update
this soon as well.

--------------------------------------------------------

This small demo shows how large files can be copied from client to a destination without requiring any temporary files,
and only requires a minimal memory footprint. For example, FireFox sends 8KB chunks when uploading files, so only a
few chunks would be held in memory for each client performing a transfer.

Large uploads, such as movies, should not be completely buffered by the Play app before copying the data to the destination.
The [Play 2.1 Iteratee examples](http://www.playframework.com/documentation/2.1.0/ScalaFileUpload) for doing file upload
do not explain how to use small in-memory buffers to stream an upload from the client to a destination.

This demo streams a File upload to a local file; you could easily modify this example to stream elsewhere, such as
AWS S3 using the [AWS Java SDK](http://aws.amazon.com/documentation/sdkforjava/).

Just clone the repo and run it with Play on your local machine:
<pre>play debug run</pre>

You can upload using curl instead of the web browser. Thanks to Andrew Gaydenko for the incantation:
<pre>curl -i --no-keepalive -F name=myFile.mp4 -F filedata=@myFile.mp4 http://localhost:9000/upload</pre>

Files are uploaded to ~/uploadedFiles.

You'll find inline comments in the code.
