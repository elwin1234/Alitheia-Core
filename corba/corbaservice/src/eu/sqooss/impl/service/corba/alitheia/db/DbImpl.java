package eu.sqooss.impl.service.corba.alitheia.db;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.omg.CORBA.Any;
import org.omg.CORBA.AnyHolder;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

import eu.sqooss.core.AlitheiaCore;
import eu.sqooss.impl.service.corba.alitheia.DatabasePOA;
import eu.sqooss.impl.service.corba.alitheia.map_entry;
import eu.sqooss.service.db.DBService;

/**
 * Wrapper class to enable the Database to be exported into the
 * Corba ORB.
 * @author Christoph Schleifenbaum, KDAB
 */
public class DbImpl extends DatabasePOA {
   
    protected DBService db = null;

	public DbImpl(BundleContext bc) {
		ServiceReference serviceRef = bc.getServiceReference(AlitheiaCore.class.getName());
        AlitheiaCore core = (AlitheiaCore) bc.getService(serviceRef);
        if (core == null) {
            System.out.println("CORBA database could not get the Alitheia core");
            return;
        }
        db = core.getDBService();
        DAObject.db = db;
    }

	/**
	 * Add a new record to the system database, using the default database
	 * session. This should initialize any tables that are needed for storage of 
	 * project information.
	 * @param dbObject the record to persist into the database
	 * @return true if the record insertion succeeded, false otherwise
	 */
    public boolean addRecord(AnyHolder dbObject) {
        db.startDBSession();
        eu.sqooss.service.db.DAObject obj = DAObject.fromCorbaObject(dbObject.value);
        boolean result = db.addRecord(obj);
        dbObject.value = DAObject.toCorbaObject(obj);
        db.commitDBSession();
        return result;
    }

    /**
     * Update an existing record in the system database, using the default database session.
     *
     * @param record the record to update in the database
     * @return true if the record update succeeded, false otherwise
     */
    public boolean updateRecord(AnyHolder record) {
        db.startDBSession();
        eu.sqooss.service.db.DAObject obj = DAObject.fromCorbaObject(record.value);
        if (obj.getId()==0) {
            db.commitDBSession();
            return false;
        }
        //boolean result = db.updateRecord(obj);
        record.value = DAObject.toCorbaObject(obj);
        db.commitDBSession();
        return true;
    }

    /**
     * Delete an existing record from the system database, using the default 
     * database session.
     * @param record the record to remove from the database
     * @return true if the record deletion succeeded, false otherwise
     */
    public boolean deleteRecord(Any record) {
        db.startDBSession();
        boolean result = db.deleteRecord(DAObject.fromCorbaObject(record));
        db.commitDBSession();
        return result;
    }

    /**
     * A generic query method to retrieve a single DAObject subclass using its 
     * identifier. The return value is parameterized to the actual type of 
     * DAObject queried so no downcast is needed.
     * @param id the DAObject's identifier
     * @return the DAOObject if a match for the class and the identifier was 
     * found in the database, or null otherwise or if a database access error occured
     */
    @SuppressWarnings("unchecked")
    public Any findObjectById(Any type, int id) {
        db.startDBSession();
        Class<eu.sqooss.service.db.DAObject> classType = 
            (Class<eu.sqooss.service.db.DAObject>) DAObject.fromCorbaType(type);
         eu.sqooss.service.db.DAObject obj = db.findObjectById(classType, id);
        Any result = DAObject.toCorbaObject(obj);
        db.commitDBSession();
        return result;
    }

    protected static Map<String, Object> arrayToMap(map_entry[] array) {
        Map<String, Object> result = new HashMap<String, Object>();
        for(map_entry e : array) {
            result.put(e.key, DAObject.fromCorbaObject(Object.class, e.value));
        }
        return result;
    }
    
    @SuppressWarnings("unchecked")
    public Any[] findObjectsByProperties(Any type, map_entry[] properties) {
        db.startDBSession();
        Map<String, Object> propMap = arrayToMap(properties);
        Class<eu.sqooss.service.db.DAObject> classType = 
            (Class<eu.sqooss.service.db.DAObject>) DAObject.fromCorbaType(type);
        List<eu.sqooss.service.db.DAObject> objects = db.findObjectsByProperties(classType, propMap);
        Any[] result = new Any[objects.size()];
        for (int i = 0; i < objects.size(); ++i) {
            result[i] = DAObject.toCorbaObject(objects.get(i));
        }
        db.commitDBSession();
        return result;
    }

    public Any[] doHQL(String hql, map_entry[] params) {
        db.startDBSession();
        Map<String, Object> propMap = arrayToMap(params);
        List<?> objects = db.doHQL(hql, propMap);
        Any[] result = new Any[objects.size()];
        for (int i = 0; i < objects.size(); ++i) {
            Object o = objects.get(i);
            if (o instanceof eu.sqooss.service.db.DAObject)
                result[i] = DAObject.toCorbaObject((eu.sqooss.service.db.DAObject) objects.get(i));
            else if (o instanceof Long)
                result[i].insert_long(((Long)o).intValue());
            else if (o instanceof Boolean)
                result[i].insert_boolean(((Boolean)o).booleanValue());
            else if (o instanceof String)
                result[i].insert_string((String)o);
        }
        db.commitDBSession();
        return result;
    }

    public Any[] doSQL(String sql, map_entry[] params) {
        db.startDBSession();
        Map<String, Object> propMap = arrayToMap(params);
        List<?> objects;
        Any[] result = null;
        try {
            objects = db.doSQL(sql, propMap);
        
            result = new Any[objects.size()];
            for (int i = 0; i < objects.size(); ++i) {
                Object o = objects.get(i);
                if (o instanceof eu.sqooss.service.db.DAObject)
                    result[i] = DAObject.toCorbaObject((eu.sqooss.service.db.DAObject) objects.get(i));
                else if (o instanceof Long)
                    result[i].insert_long(((Long)o).intValue());
                else if (o instanceof Boolean)
                    result[i].insert_boolean(((Boolean)o).booleanValue());
                else if (o instanceof String)
                    result[i].insert_string((String)o);
            }
            db.commitDBSession();
        } catch (Exception e) {
            db.rollbackDBSession();
            return null;
        }
        return result;
    }
}
