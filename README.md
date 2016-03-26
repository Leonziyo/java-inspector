# java-inspector

# Introduction

My inspiration to building this project was to reduce the time of testing and debugging. When I was working in projects specifically in Game Development I found myself requiring to adjust or updated values in classes or files and re-launch the app to see the difference. Re-launching an app could take quite a while specially if you are doing it quite often. In response to that I would add UI elements in the game to update those values at runtime, but I found it was not ideal to do that for every project. So I started this project to completely separate the Testing part of an app into a different project, and to make the incorportation of the testing module and the app very easy, by just adding a couple lines of code.

This library project for Android allows you to inspect and modify objects at runtime. It is similar to setting breakpoints in your project and use the debugger but when using this library you don't need to set breakpoints and inspecting objects is much faster and easier. You need to use a Node JS app (which I also provide in a different repo) and you can access a nice UI from a web browser to start inspecting your objects. A nice feature of this library is that you can choose what objects to inspect. So far you can only change primitive types but later on I will be adding support for Objects.

# Features

- Register object with library to start tracking in the UI
- Update any primitive type in the object (also inherited properties, private, protected fields too)
- The UI is updated with the latest changes every so often to reflect changes in ojects.



# Installation

This project is meant to be used in conjunction with a Node js app which provides the UI. Don't worry it all is very simple and fast to do.

- Install [Node JS](https://nodejs.org/en/), the website is very self explanatory.
- Downlaod or check out my [Node JS app](https://github.com/Leonziyo/java-inspector-nodeapp).
- After that is just as easy as opening the terminal and running:

```
cd path/to/app/root/directory # change to the root directory of the repo (the one you downloaded)
node app/index.js # that is it
```

Now back to the Android project. You need to add the dependecy to your gradle files. First open the one for the project located at the root directory of your app it is called `build.gradle` and add this dependency:

```
dependencies {
    ...
    compile 'com.github.leonziyo:java-inspector:v0.1'
}
```

Now open the gradle file for your app module it is the one located at: `app/build.gradle`

```
allprojects {
    repositories {
        ...
        maven { url "https://jitpack.io" }
    }
}
```

After that just let the project sync with gradle. If no errors then you are done and ready!

# Documentation

If you prefer you can take a look at the sample project in this repo, make sure you have the Node JS app running like mentioned above and run the sample project.
You will be able to access the UI by going to [http://localhost:8080/](http://localhost:8080/).

Remember to add the internet permission in you project manifest if you haven't already. Is is required for the app to interact with your Node JS server and nothing else.

`<uses-permission android:name="android.permission.INTERNET"></uses-permission>`

There is very little you need to do to get it set up. There is only one class in this library and it is called `Updater`.
So just import it `import com.leonziyo.javainspector;`

The class is a singleton the first time you call it you must call it like this:

```
Updater updater = Updater.getInstance(
    this.getClass().getPackage().getName(), // the root package name for your project
    "http://192.168.1.72:8080/" //change for your own Node app server ip.
);
```

**NOTE:**
Do not use localhost because when running it on a device it will not find your server.

After that you can just call it like this:

```
Updater updater = Updater.getInstance();
```

Now you can simply call `register` and that will add the object to the list of object to track down by the UI, call it like this:

```
Updater updater = Updater.getInstance();
updater.register(anyObject);
```

If you do not want to track down the object anymore just call:

```
Updater updater = Updater.getInstance();
updater.unregister(anyObject);
```

You can now run the app, make sure the Node app is running too. Go to [http://localhost:8080/](http://localhost:8080/) and you will see a nice UI where you can edit the values of each variable.

A good idea is to simply register the objects in the constructor and that way you will track all the instances of that class. Like this:

```
public class SampleClass {

    public SampleClass() {
        Updater updater = Updater.getInstance();
        updater.register(this);
    }
}
```
