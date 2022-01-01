# Voiceprint

This is a simple service which helps you to avoid printing text manually.
It uses neural network ASR engine under the hood which is converting your voice into text on flight.

Another part of engine will prepare documents with different formats for your future operations.

Fill free to use.

### Sequence diagram
![diagram](pictures/sequencediagram.png "sequencediagram") 

### How to test quickly

- create google cloud account, enable google ASR and create env variable `ASR_CREDENTIALS_LOCATION` which is contains path to the gcp token;
- alternatively user may add gcp token data inside `src/main/java/org/voiceprint/creds.json`
- prepare some audio file with English speech in `wav` format  
- install ffmpeg as described here: https://www.ffmpeg.org/download.html
- run from the terminal the following command: `ffmpeg -re -i <path_to_file_with_English_speech_in_wav_format> -acodec pcm_s16le -f s16le -ac 1 -ar 16000 tcp://localhost:8081`
- see debugging output in the console


### Enhancements 

- add AWS and Azure ASRs
- make a possibility to write data inside file
- create frontend :)