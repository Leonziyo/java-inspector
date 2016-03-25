package com.leonziyo.javainspector;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class Updater {

    static Updater mInstance;

    static final String
        REGISTER_OBJECT_POOL = "REGISTER_OBJECT_POOL",
        FIELD_UPDATED_AT_WEB_CLIENT = "FIELD_UPDATED_AT_WEB_CLIENT",
        UPDATE_ALL_OBJECT_POOLS = "UPDATE_ALL_OBJECT_POOLS",
        ADD_OBJECT_TO_POOL = "ADD_OBJECT_TO_POOL",
        REMOVE_OBJECT_FROM_POOL = "REMOVE_OBJECT_FROM_POOL",
        CLEAR_ALL_POOLS = "CLEAR_ALL_POOLS";

    List<ObjectPool> mObjectPools;
    Socket mSocket;
    String mEndpoint;
    String mPackageName;
    Timer mTimer;

    int updateTime = 3000; // milliseconds

    private Updater(String packageName, String endpoint) {
        this.mPackageName = packageName;
        this.mEndpoint = endpoint;
        this.mObjectPools = new ArrayList<>();

        connectSocket();
        // TODO
        // - Create a method in this class, it will allow to set the current object in the web interface
        //      for example: tapping an object and detecting the tap with your application logic, then you call setObjectForUI(object)
        //      the object has to be registered with a pool object before calling that method.
    }

    public void startTimer() {
        if(mTimer != null || !mSocket.connected())
            return;

        mTimer = new Timer();
        mTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                mSocket.emit(UPDATE_ALL_OBJECT_POOLS, getAllObjectPools());
            }
        }, updateTime, updateTime);
    }

    public void stopTimer() {
        if(mTimer != null) {
            mTimer.cancel();
            mTimer = null;
        }
    }

    public void clearAllPools() {
        mObjectPools.clear();
        mSocket.emit(CLEAR_ALL_POOLS);
    }

    private JSONArray getAllObjectPools() {
        JSONArray array = new JSONArray();

        for(ObjectPool next : mObjectPools)
            array.put(next.toJSON());

        return array;
    }

    public static Updater getInstance(String packageName, String endpoint) {
        if(mInstance == null) {
            mInstance = new Updater(packageName, endpoint);
        }
        return mInstance;
    }

    public static Updater getInstance() {
        return mInstance;
    }

    private static void log(String msg) {
        System.out.print(msg);
    }

    private void connectSocket() {
        try {
            mSocket = IO.socket(mEndpoint);
        }
        catch(URISyntaxException e) {
            log(e.getMessage());
        }

        mSocket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {

            @Override
            public void call(Object... args) {
                startTimer();
            }

        }).on(FIELD_UPDATED_AT_WEB_CLIENT, new Emitter.Listener() {

            @Override
            public void call(Object... args) {
                JSONObject obj = (JSONObject) args[0];
                updateObjectPoolObject(obj);
            }

        }).on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {

            @Override
            public void call(Object... args) {
                stopTimer();
            }

        });
        mSocket.connect();
    }

    private void updateObjectPoolObject(JSONObject object) {
        //log("update object form web client");

        //Update single field in an object inside pool
        try {
            ObjectPool pool = getObjectPoolForClass(object.getString("class_name"));
            int objectId = object.getInt("object_id");
            JSONObject field = object.getJSONObject("field");
            Object value = null;
            switch(field.getString("type")) {
                case "float":
                    value = (float)field.getDouble("value");
                    break;
                case "int":
                    value = field.getInt("value");
                    break;
                case "byte":
                    value = field.getInt("value");
                    break;
                case "double":
                    value = field.getDouble("value");
                    break;
                case "long":
                    value = field.getLong("value");
                    break;
                case "boolean":
                    value = field.getBoolean("value");
                    break;
                case "char":
                    value = field.getString("value").charAt(0);
                    break;
                case "short":
                    value = (short)field.getInt("value");
                    break;
            }

            for(ObjectIdPair next : pool.mObjects) {
                if(next.id == objectId) {
                    pool.setFieldValueForObject(next.object, value, field.getString("name"));
                    break;
                }
            }
        }
        catch(JSONException e) {
            log(e.getMessage());
        }

    }

    public void register(Object obj) {
        ObjectPool pool = getObjectPoolForClass(obj.getClass());
        if(pool == null) {
            pool = new ObjectPool(obj.getClass(), getInheritedPrivateFields(obj.getClass()));
            if(mObjectPools.add(pool))
                mSocket.emit(REGISTER_OBJECT_POOL, pool.toJSON());
        }

        if(pool.addObject(obj)) {
            mSocket.emit(ADD_OBJECT_TO_POOL, pool.getObjectJsonAt(obj));
        }
    }

    public void unregister(Object obj) {
        ObjectPool pool = getObjectPoolForClass(obj.getClass());
        if(pool == null) {
            log("Warning: no pool found when unregistering.");
            return;
        }

        if(pool.removeObject(obj)) {
            mSocket.emit(REMOVE_OBJECT_FROM_POOL, pool.getObjectJsonAt(obj));
        }
    }

    private List<Field> getInheritedPrivateFields(Class<?> type) {
        List<Field> result = new ArrayList<Field>();

        Class<?> i = type;
        while (i != null && i != Object.class) {
            Field[] temp = i.getDeclaredFields();
            for(Field next : temp) {
                //if(next.getType().getName().contains(mPackageName))
                //if (Modifier.isPrivate(next.getModifiers()))
                    next.setAccessible(true);

                if(next.getType().isPrimitive()) {
                    result.add(next);
                }
            }
            i = i.getSuperclass();
        }

        return result;
    }

    private ObjectPool getObjectPoolForClass(Class<?> clazz) {
        for(ObjectPool next : mObjectPools) {
            if(next.mClazz.getName().equals(clazz.getName()))
                return next;
        }

        return null;
    }

    private ObjectPool getObjectPoolForClass(String clazz) {
        for(ObjectPool next : mObjectPools) {
            if(next.mClazz.getName().equals(clazz))
                return next;
        }

        return null;
    }

    public static class ObjectPool {
        Class<?> mClazz;
        List<Field> mFields;
        List<ObjectIdPair> mObjects;

        public ObjectPool(Class<?> clazz, List<Field> fields) {
            this.mClazz = clazz;
            this.mFields = fields;
            this.mObjects = new ArrayList<>();
        }

        public boolean addObject(Object obj) {
            return this.mObjects.add(new ObjectIdPair(getUniqueId(), obj));
        }

        public boolean removeObject(Object obj) {
            return this.mObjects.remove(getObjectIdPairByObject(obj));
        }

        private int getUniqueId() {
            Random ran = new Random();
            int val = ran.nextInt(999999999);
            for(int i = 0; i < mObjects.size(); i++) {
                if(mObjects.get(i).id == val) {
                    val = ran.nextInt(999999999);
                    i = -1; // reset the loop
                }
            }
            return val;
        }

        public void setFieldValueForObject(Object obj, Object value, String fieldName) {
            try {
                for (Field next : mFields) {
                    if (next.getName().equals(fieldName))
                        next.set(obj, value);
                }
            }
            catch(IllegalAccessException e) {
                log(e.getMessage());
            }
        }

        private ObjectIdPair getObjectIdPairByObject(Object obj) {
            for(ObjectIdPair next : mObjects) {
                if (next.object == obj)
                    return next;
            }
            return null;
        }

        public JSONObject toJSON() {
            JSONObject obj = new JSONObject();
            try {
                obj.put("class_name", mClazz.getName());
                JSONArray fields = new JSONArray();
                obj.put("fields", fields);

                JSONArray objects = new JSONArray();
                obj.put("objects", objects);

                for(Field nextField : mFields) {
                    JSONObject newField = new JSONObject();
                    newField.put("name", nextField.getName());
                    newField.put("type", nextField.getType());
                    fields.put(newField);

                    // access each object
                    for(int i = 0; i < mObjects.size(); i++) {
                        JSONObject theObj;
                        JSONArray objFields;
                        if(objects.length() > i) {
                            theObj = (JSONObject)objects.get(i);
                            objFields = theObj.getJSONArray("fields");
                        }
                        else {
                            theObj = new JSONObject();
                            theObj.put("id", mObjects.get(i).id);
                            objects.put(theObj);
                            objFields = new JSONArray();
                            theObj.put("fields", objFields);
                        }

                        try {
                            JSONObject temp = new JSONObject();
                            temp.put("name", nextField.getName());
                            temp.put("value", nextField.get(mObjects.get(i).object));
                            temp.put("type", nextField.getType());

                            objFields.put(temp);
                        }
                        catch(IllegalAccessException e) {
                            Updater.log(e.getMessage());
                        }


                    }
                }
            }
            catch(JSONException e) {
                Updater.log(e.getMessage());
            }

            //String result = obj.toString();
            return obj;
        }

        public JSONObject getObjectJsonAt(Object o) {
            int index = mObjects.indexOf(getObjectIdPairByObject(o));
            return getObjectJsonAt(index);
        }

        public JSONObject getObjectJsonAt(int index) {
            if(index > mObjects.size()-1 || index < 0)
                return null;

            JSONObject theObj = new JSONObject();
            JSONArray objFields = new JSONArray();

            try {
                theObj.put("class_name", mClazz.getName());
                theObj.put("id", mObjects.get(index).id);
                theObj.put("fields", objFields);


                for(Field nextField : mFields) {
                    try {
                        JSONObject temp = new JSONObject();
                        temp.put("name", nextField.getName());
                        temp.put("value", nextField.get(mObjects.get(index).object));
                        temp.put("type", nextField.getType());

                        objFields.put(temp);
                    }
                    catch(IllegalAccessException e) {
                        Updater.log(e.getMessage());
                    }
                }
            }
            catch(JSONException e) {
                Updater.log(e.getMessage());
            }

            return theObj;
        }
    }

    public static class ObjectIdPair {
        public int id;
        public Object object;

        public ObjectIdPair(int id, Object object) {
            this.id = id;
            this.object = object;
        }
    }
}
