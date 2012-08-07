package net.chrislehmann.contentproviderdao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DaoTemplate {

    private Context context;

    public DaoTemplate(Context context) {
        this.context = context;
    }

    public DaoTemplate() {
    }

    public <T extends Object> T save(T object) {
        if (object.getClass().isAnnotationPresent(Content.class)) {

            java.lang.reflect.Field field = getIdField(object.getClass());
            Object idValue = getValue(object, field);

            ContentValues contentValues = createContentValuesFromObject(object);
            Uri uri = Uri.parse(object.getClass().getAnnotation(Content.class).contentUri());

            if (idValue != null) {
                context.getContentResolver().update(
                        uri, contentValues, field.getAnnotation(Field.class).columnName() + " = ?", new String[]{idValue.toString()}
                );
            } else {
                Uri newUrl = context.getContentResolver().insert(uri, contentValues);
                String newIdString = newUrl.getLastPathSegment();
                Object newIdValue = convertStringToType(field, newIdString);
                try {
                    field.setAccessible(true);
                    field.set(object, newIdValue);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("Error setting new id on object");
                }
            }

            updateNestedCollctions(object);

        }
        return object;
    }

    private Object convertStringToType(java.lang.reflect.Field field, String idValue) {
        Class type = field.getType();
        if (String.class.equals(type)) {
            return idValue;
        } else if (type.isPrimitive() && "int".equals(type.getName()) || Integer.class.equals(type)) {
            return Integer.valueOf(idValue);
        } else if (type.isPrimitive() && "float".equals(type.getName()) || Float.class.equals(type)) {
            return Float.valueOf(idValue);
        } else if (type.isPrimitive() && "double".equals(type.getName()) || Double.class.equals(type)) {
            return Double.valueOf(idValue);
        } else if (type.isPrimitive() && "long".equals(type.getName()) || Long.class.equals(type)) {
            return Long.valueOf(idValue);
        } else if (type.isPrimitive() && "short".equals(type.getName()) || Short.class.equals(type)) {
            return Short.valueOf(idValue);
        }
        return null;
    }

    private <T> void updateNestedCollctions(T object) {
        for (java.lang.reflect.Field field : object.getClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(JoinedList.class)) {
                JoinedList list = field.getAnnotation(JoinedList.class);
                java.lang.reflect.Field idField = getIdField(object.getClass());
                idField.setAccessible(true);
                Object idValue = getValue(object, idField);

                Content listContent = (Content) list.klass().getAnnotation(Content.class);
                context.getContentResolver().delete(Uri.parse(listContent.contentUri()), list.foreignKeyColumnName() + " = ?", new String[]{idValue.toString()});

                try {
                    field.setAccessible(true);
                    List<?> newValues = (List<?>) field.get(object);
                    if (newValues != null) {

                        for (Object newValue : newValues) {
                            java.lang.reflect.Field forigenKeyField = findForignKeyForObject(list.foreignKeyColumnName(), newValue.getClass());
                            if (forigenKeyField == null) {
                                throw new RuntimeException("Could not find forgien key annotation on child class.  Did you map both sides?");
                            }

                            if (forigenKeyField.getAnnotation(ForeignKey.class).cascadeUpdates() == true) {
                                throw new RuntimeException("Bi-direction relationship exists.  Set cascade=false on one side of the relationship");
                            }

                            forigenKeyField.setAccessible(true);
                            forigenKeyField.set(newValue, object);
                            save(newValue);
                        }

                    }

                } catch (IllegalAccessException e) {
                    throw new RuntimeException("Cannot get list value", e);
                }
            }

        }

    }

    private java.lang.reflect.Field findForignKeyForObject(String forgienKeyColumnName, Class newValueClass) {
        for (java.lang.reflect.Field field : newValueClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(ForeignKey.class)) {
                if (forgienKeyColumnName.equals(field.getAnnotation(ForeignKey.class).columnName())) {
                    return field;
                }
            }
        }
        return null;
    }


    public <T> void delete(Object object, Class<T> sessionClass) {
        if (sessionClass.isAnnotationPresent(Content.class)) {
            Object id = getIdValue(object);
            deleteById(id, sessionClass);
        }
    }

    public <T> void deleteById(Object id, Class<T> sessionClass) {
        if (sessionClass.isAnnotationPresent(Content.class)) {
            Content content = sessionClass.getAnnotation(Content.class);
            java.lang.reflect.Field field = getIdField(sessionClass);

            String whereClause = String.format("%s = ?", field.getAnnotation(Field.class).columnName());
            context.getContentResolver().delete(Uri.parse(content.contentUri()), whereClause, new String[]{id.toString()});
        }
    }

    private Object getValue(Object object, java.lang.reflect.Field field) {
        Object idValue;
        try {
            field.setAccessible(true);
            idValue = field.get(object);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Error getting id value for object");
        }
        return idValue;
    }

    private ContentValues createContentValuesFromObject(Object object) {
        ContentValues contentValues = new ContentValues();

        for (java.lang.reflect.Field field : object.getClass().getDeclaredFields()) {
            try {
                if (field.isAnnotationPresent(Field.class)) {
                    String columnName = field.getAnnotation(Field.class).columnName();
                    Class type = field.getType();

                    field.setAccessible(true);
                    setValueOnContentValues(object, contentValues, field, columnName);

                } else if (field.isAnnotationPresent(ForeignKey.class)) {
                    ForeignKey foreignKey = field.getAnnotation(ForeignKey.class);
                    field.setAccessible(true);
                    java.lang.reflect.Field forgienKeyIdField = getIdField(field.getType());
                    forgienKeyIdField.setAccessible(true);
                    Object joinedObjectValue = field.get(object);
                    if (joinedObjectValue != null) {

                        setValueOnContentValues(joinedObjectValue, contentValues, forgienKeyIdField, foreignKey.columnName());

                        if (foreignKey.cascadeUpdates()) {
                            save(field.get(object));
                        }
                    }
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Cannot create content values fot field " + field.getName(), e);
            }

        }
        return contentValues;
    }

    private void setValueOnContentValues(Object object, ContentValues contentValues, java.lang.reflect.Field field, String columnName) throws IllegalAccessException {
        Class type = field.getType();
        if (type.isPrimitive()) {
            if ("int".equals(type.getName())) {
                contentValues.put(columnName, field.getInt(object));
            } else if ("float".equals(type.getName())) {
                contentValues.put(columnName, field.getFloat(object));
            } else if ("double".equals(type.getName())) {
                contentValues.put(columnName, field.getDouble(object));
            } else if (Byte.class.equals(type)) {
                contentValues.put(columnName, field.getByte(object));
            } else if ("boolean".equals(type.getName())) {
                contentValues.put(columnName, field.getBoolean(object));
            } else if ("long".equals(type.getName())) {
                contentValues.put(columnName, field.getLong(object));
            } else if ("short".equals(type.getName())) {
                contentValues.put(columnName, field.getShort(object));
            }
        } else {
            Object valueToSave = field.get(object);
            if (valueToSave == null) {
                contentValues.putNull(columnName);
            } else if (Integer.class.equals(type)) {
                contentValues.put(columnName, (Integer) valueToSave);
            } else if (Float.class.equals(type)) {
                contentValues.put(columnName, (Float) valueToSave);
            } else if (Double.class.equals(type)) {
                contentValues.put(columnName, (Double) valueToSave);
            } else if (Boolean.class.equals(type)) {
                contentValues.put(columnName, (Boolean) valueToSave);
            } else if (Long.class.equals(type)) {
                contentValues.put(columnName, (Long) valueToSave);
            } else if (Short.class.equals(type)) {
                contentValues.put(columnName, (Short) valueToSave);
            } else if (String.class.equals(type)) {
                contentValues.put(columnName, (String) field.get(object));
            } else if (type.isEnum()) {
                contentValues.put(columnName, valueToSave == null ? null : valueToSave.toString());
            } else if (Date.class.equals(type)) {
                Date date = (Date) field.get(object);
                contentValues.put(columnName, date.getTime());
            }
        }

    }

    public void setValue(Cursor c, java.lang.reflect.Field f, Object obj) {
        int columnIndex = c.getColumnIndex(f.getAnnotation(Field.class).columnName());

        Class<? extends Object> type = f.getType();
        f.setAccessible(true);
        try {
            if (String.class.equals(type)) {
                f.set(obj, c.getString(columnIndex));
            } else if (type.isPrimitive() && "int".equals(type.getName()) || Integer.class.equals(type)) {
                f.set(obj, c.getInt(columnIndex));
            } else if (type.isPrimitive() && "float".equals(type.getName()) || Float.class.equals(type)) {
                f.set(obj, c.getFloat(columnIndex));
            } else if (type.isPrimitive() && "double".equals(type.getName()) || Double.class.equals(type)) {
                f.set(obj, c.getDouble(columnIndex));
            } else if (Byte[].class.equals(type)) {
                f.set(obj, c.getBlob(columnIndex));
            } else if (type.isPrimitive() && "boolean".equals(type.getName()) || Boolean.class.equals(type)) {
                f.set(obj, c.getInt(columnIndex) != 0);
            } else if (type.isPrimitive() && "long".equals(type.getName()) || Long.class.equals(type)) {
                f.set(obj, c.getLong(columnIndex));
            } else if (type.isPrimitive() && "short".equals(type.getName()) || Short.class.equals(type)) {
                f.set(obj, c.getInt(columnIndex));
            } else if (type.isEnum()) {
                Class<? extends Enum> enumType = (Class<? extends Enum>) type;
                String value = c.getString(columnIndex);
                if (value != null) {
                    Enum val = Enum.valueOf(enumType, value);
                    f.set(obj, val);
                }
            } else if (Date.class.equals(type)) {
                long seconds = c.getLong(columnIndex);
                f.set(obj, new Date(seconds));
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Error setting value", e);
        }
    }

    public Cursor loadCursor(Object id, Class<?> klass) {
        if (id == null) {
            return null;
        }
        Uri uri = Uri.parse(klass.getAnnotation(Content.class).contentUri());
        java.lang.reflect.Field idField = getIdField(klass);

        String idColumnName = idField.getAnnotation(Field.class).columnName();
        return context.getContentResolver().query(uri, null, String.format("%s = ?", idColumnName), new String[]{id.toString()}, null);
    }


    public <T extends Object> Result<T> load(Object id, Class<T> klass) {

        if (id == null) {
            return new Result<T>(null, null);
        }

        Cursor c = loadCursor(id, klass);

        if (c != null && c.getCount() > 1) {
            throw new RuntimeException("Found multiple rows, but only expected one!");
        }
        T instance = createSingleInstanceFromCursor(klass, c);
        return new Result<T>(instance, c);
    }

    public <T extends Object> T loadObject(Object id, Class<T> type) {
        Result<T> result = load(id, type);
        result.close();
        return result.object;
    }

    public <T> Result<List<T>> query(Class<T> klass, String queryString, String... args) {
        return queryWithParent(klass, null, queryString, args);
    }

    public Cursor queryForCursor(Class<?> klass, String queryString, String... args) {
        Uri uri = Uri.parse(klass.getAnnotation(Content.class).contentUri());
        return context.getContentResolver().query(uri, null, queryString, args, null);
    }


    public <T> Result<List<T>> queryWithParent(Class<T> klass, Object parentObject, String queryString, String... args) {
        List<T> resultList = new ArrayList<T>();
        Cursor cursor = queryForCursor(klass, queryString, args);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                resultList.add(createInstanceFromCursor(klass, cursor, parentObject));
            }
        }

        return new Result<List<T>>(resultList, cursor);
    }

    public <T> List<T> queryForList(Class<T> klass, String queryString, String... args) {
        Result<List<T>> result = query(klass, queryString, args);
        result.close();
        return result.object;
    }


    public <T> Result<T> queryForSingleResult(Class<T> klass, String queryString, String... args) {
        Result<List<T>> result = query(klass, queryString, args);
        if (result.object.size() > 1) {
            throw new RuntimeException("Expected one result, got " + result.object.size());
        }

        if (result.object.size() == 1) {
            return new Result<T>(result.object.get(0), result.cursor);
        }
        return null;

    }

    public <T> T queryForSingleObject(Class<T> klass, String queryString, String... args) {
        Result<T> result = queryForSingleResult(klass, queryString, args);
        if (result == null) {
            return null;
        }
        result.close();
        return result.object;
    }

    public <T extends Object> T loadObjectFromCursor(Cursor cursor, Class<T> klass) {
        return createSingleInstanceFromCursor(klass, cursor);
    }


    public <T extends Object> T queryForSingleFlatObject(Class<T> klass, String queryString, String... params) {
        T instance = null;
        Cursor c = queryForCursor(klass, queryString, params);
        if(c != null && c.getCount() > 0){
            c.moveToFirst();
            instance = loadFlatObjectFromCursor(klass, c);
        }
        return instance;
    }

    public <T extends Object> T loadFlatObjectFromCursor(Class<T> objectClass, Cursor cursor) {
        T instance;
        try {
            instance = objectClass.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Error creating class " + objectClass, e);
        }

        //Set the id first
        java.lang.reflect.Field idField = getIdField(objectClass);
        setValue(cursor, idField, instance);

        for (java.lang.reflect.Field field : objectClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(Field.class)) {
                setValue(cursor, field, instance);
            }
        }

        return instance;

    }

    private <T extends Object> T createSingleInstanceFromCursor(Class<T> sessionClass, Cursor c) {
        T instance = null;
        if (c != null && c.getCount() > 0) {
            c.moveToFirst();
            instance = createInstanceFromCursor(sessionClass, c);
        }
        return instance;
    }

    private <T extends Object> T createInstanceFromCursor(Class<T> objectClass, Cursor cursor) {
        return createInstanceFromCursor(objectClass, cursor, null);
    }


    private <T extends Object> T createInstanceFromCursor(Class<T> objectClass, Cursor cursor, Object parentObject) {

        T instance = loadFlatObjectFromCursor(objectClass, cursor);

        for (java.lang.reflect.Field field : objectClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(ForeignKey.class)) {
                setJoinedClassValue(objectClass, cursor, instance, field, parentObject);
            } else if (field.isAnnotationPresent(JoinedList.class)) {
                setJoinedListValue(objectClass, cursor, instance, field);
            }
        }
        return instance;
    }

    private <T extends Object> void setJoinedListValue(Class<T> objectClass, Cursor cursor, T instance, java.lang.reflect.Field field) {
        JoinedList list = field.getAnnotation(JoinedList.class);
        Object keyValue = getIdValue(objectClass, cursor);
        Result<List<?>> joinedResults = queryWithParent(list.klass(), instance, list.foreignKeyColumnName() + " = ?", keyValue.toString());
        field.setAccessible(true);
        try {
            field.set(instance, joinedResults.object);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Cannot set list field", e);
        }
    }

    private <T extends Object> Object getIdValue(Class<T> objectClass, Cursor cursor) {
        java.lang.reflect.Field idTypeField = getIdField(objectClass);
        Field field = idTypeField.getAnnotation(Field.class);
        return getValue(cursor, field.columnName(), idTypeField.getType());
    }

    private <T extends Object> void setJoinedClassValue(Class<T> sessionClass, Cursor c, T instance, java.lang.reflect.Field field, Object parentObject) {
        ForeignKey foreignKey = field.getAnnotation(ForeignKey.class);
        Class<?> foreignKeyType = field.getType();
        java.lang.reflect.Field foreignKeyIdType = getIdField(foreignKeyType);
        Object keyValue = getValue(c, foreignKey.columnName(), foreignKeyIdType.getType());
        field.setAccessible(true);

        //Don't try and reload our parent object....
        if (parentObject != null && parentObject.getClass() == field.getType()) {
            Object parentObjectId = getIdValue(parentObject);
            if (parentObject != null && keyValue.equals(parentObjectId)) {
                try {
                    field.set(instance, parentObject);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("Cannot set joined value on field " + field.getName());
                }
                return;
            }
        }

        Object object = loadObject(keyValue, foreignKeyType);

        try {
            field.set(instance, object);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Cannot set joined value on field " + field.getName());
        }
    }

    private Object getIdValue(Object object) {
        java.lang.reflect.Field idField = getIdField(object.getClass());
        return getValue(object, idField);
    }

    private Object getValue(Cursor c, String columnName, Class type) {
        Object value = null;
        int columnIndex = c.getColumnIndex(columnName);

        if (String.class.equals(type)) {
            value = c.getString(columnIndex);
        } else if (type.isPrimitive() && "int".equals(type.getName()) || Integer.class.equals(type)) {
            value = c.getInt(columnIndex);
        } else if (type.isPrimitive() && "float".equals(type.getName()) || Float.class.equals(type)) {
            value = c.getFloat(columnIndex);
        } else if (type.isPrimitive() && "double".equals(type.getName()) || Double.class.equals(type)) {
            value = c.getDouble(columnIndex);
        } else if (Byte[].class.equals(type)) {
            value = c.getBlob(columnIndex);
        } else if (type.isPrimitive() && "boolean".equals(type.getName()) || Boolean.class.equals(type)) {
            value = c.getInt(columnIndex) != 0;
        } else if (type.isPrimitive() && "long".equals(type.getName()) || Long.class.equals(type)) {
            value = c.getInt(columnIndex);
        } else if (type.isPrimitive() && "short".equals(type.getName()) || Short.class.equals(type)) {
            value = c.getInt(columnIndex);
        }
        return value;
    }


    private <T extends Object> java.lang.reflect.Field getIdField(Class<T> sessionClass) {
        for (java.lang.reflect.Field field : sessionClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(Field.class)) {
                if (field.getAnnotation(Field.class).id()) {
                    return field;
                }
            }
        }
        throw new RuntimeException("Cannto find ID field for class " + sessionClass);
    }

    private <T> T getValue(java.lang.reflect.Field field, Object object, Class<T> type) {
        try {
            return (T) field.get(object);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Error getting field...");
        }
    }

    public <T> T loadFlatObject(int id, Class<T> klass) {
        T instance = null;
        Cursor cursor = loadCursor(id, klass);
        if( cursor != null && cursor.getCount() > 0){
            cursor.moveToFirst();
            instance = loadFlatObjectFromCursor(klass, cursor);
        }
        return instance;
    }


    public class Result<T> {
        public T object;
        public Cursor cursor;
        Map<String, Cursor> joinedCursors = new HashMap<String, Cursor>();

        public Result(T object, Cursor cursor) {
            this.object = object;
            this.cursor = cursor;
        }

        public void addJoinedCursor(String fieldName, Cursor cursor) {
            joinedCursors.put(fieldName, cursor);
        }

        public Cursor getResultForField(String fieldName) {
            return joinedCursors.get(fieldName);
        }

        public void close() {
            for (Cursor joinedCursor : joinedCursors.values()) {
                if (joinedCursor != null && !joinedCursor.isClosed()) {
                    joinedCursor.close();
                }
            }
            joinedCursors.clear();

            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }

    }

//    public static Object getField
}
