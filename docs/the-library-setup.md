---
layout: default
title: 3. The library setup
nav_order: 3
---

## 3. The library setup

Softnet Java Library is built on Java SE 1.7. To make use of Softnet, you have to configure your project to include library files "softnet.jar" and "asncodec.jar".  

In Eclipse IDE, open the dialog "Project => Properties => Java Build Path => Libraries", click the button "Add External JARs" and browse for the library files.  

If you are working in Geany which is a default IDE on Raspbian and many other platforms, the library setup is slightly more complicated. In the Build section of the IDE’s main menu, you have three submenu items: Compile, Make and Execute. Also, on the main toolbar you have three buttons associated with them, and named Compile, Build and Run. You need to assign command text to the menu items. Let’s assume you have a class "ServiceApp" that contains a method main, which is an entry point of your program. Appropriate java file is located in the app directory. Also, let’s assume you put the library files in the "lib" folder also located in the app directory. Now, open a dialog through the menu path "Build => Set Build Commands" and specify the following commands:
1. In the section &lt;Java Commands&gt; there is an item "Compile". The appropriate command text: <span class="text-orange">javac -cp "lib/*" "%f"</span>.
2. In the section &lt;Independent commands&gt; there is an item "Make". Set this command to create a jar-file. The appropriate text is the following: <span class="text-orange">jar -cfm myservice.jar manifest.mf *.class</span>. Here, "myservice.jar" is a jar-file to be created. The next parameter "manifest.mf" is a manifest file you have to put in the app directory. It should be in UTF-8 format without a BOM and have an empty line in the end. For our assumed app configuration, this file may have the following contents:  
```
Manifest-Version: 1.0
Main-Class: ServiceApp
Class-Path: lib/softnet.jar lib/asncodec.jar
```
3. In the section &lt;Execute commands&gt; there is an item "Execute". The appropriate command text on OS *nix: <span class="text-orange">java -cp <span class="text-highlighted">lib/*:.</span> "%e"</span>, and on OS Windows: <span class="text-orange">java -cp <span class="text-highlighted">"lib/*;."</span> "%e"</span>. Pay attention to the text highlighted in yellow.
