# Cloud Sync

## Introduction
This is a Java application (currently at proof of concept stage) to enable automated backup of files to the cloud. This
helps to prevent the impact of a 'digital blackhole' by silently transferring files from specified folders to a remote
storage container on a continuous basis. This application will never modify files on the local file system - it isn't a
two-way sync tool like Dropbox. Information only flows from the local computer to the cloud, except when a backup is
being restored from the cloud (and this will go into an empty directory and never overwrite files).

When a file is added or modified on the local file system, and if that folder is within a backup set configured by you,
this will automatically be pushed into your private cloud store in an encrypted fashion. It won't be accessible to anyone 
else. Similarly, when a file is deleted from your local file system, if it was previously uploaded to the cloud, it will 
then be deleted and no longer backed up in the cloud.

Currently, this application supports syncing to Microsoft Azure, where files are stored in an encrypted fashion.

## TODO
As noted, this application is a proof of concept. There are a number of important features that still do not exist. A 
high level summary of these features include:

* At present there is only the backup engine. This means there is no way for a user to interact with the application.
An embedded web service, socket server, or similar needs to be created as a way for other software to interact with the
backup engine.

* Once the API is exposed, a client-side UI needs to be developed (either as a JavaFX application or a website accessible
through localhost).

* There is a lot of missing API:
  * Adding / removing / configuring backup sets
  * Full and partial restoring of a backup set from the cloud to the local file system.
  * Getting details of files stored in the cloud (total file size, file list, etc)
  
* The application should run as a background service on Windows / MacOS / Linux, so that it is always watching for 
changes.

## Usage
At present there is no user-friendliess at all in this application. To run the code as it is today, you must do the 
following:
 
1. Create an Azure Storage account (more details on how and what to do will come soon)
2. Clone this repo onto local machine
3. Ensure you have Maven installed and on path
4. Create a config.json file in the root of the cloned directory, and add configuration along the following lines to it:
    ```json
    {
      "azureAccountName": "<put Azure Storage account name here>",
      "azureAccountKey": "<put Azure Storage account key here>",
      "backups": [
        {
          "name": "testone",
          "root": "/Users/jonathan/backup/test1"
        },
        {
          "name": "testonetwo",
          "root": "/Users/jonathan/backup/test2"
        }
      ]
    }
    ```
5. Run the application using `mvn clean package exec:java`