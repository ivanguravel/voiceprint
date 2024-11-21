# Audio Content Categorization in streaming

This is a simple service which will help you to do the content categorization.
It's based on AWS Transcribe + Netty + Aho-Corasick (could be replaced by the categorization neural network).

Fill free to use.

### How to test quickly

- create AWS account and pass AWS credentials in some way;
- install ffmpeg as described here: https://www.ffmpeg.org/download.html
- run the `Server.java` from your IDE
- run from the terminal the following command: `ffmpeg -re -i Top50AWSServices.wav -acodec pcm_s16le -f s16le -ac 1 -ar 16000 tcp://localhost:8081`
- see categorized content in the console
