/*
 * IdeaJdbc is a JDBC Data Access Framework. It depends on IdeaData Framework.
 *
 * copyright © www.ideamoment.com
 */
package com.ideamoment.ideajdbc.tool.mysql.entity2ddl;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ideamoment.ideadata.annotation.DataItemType;
import com.ideamoment.ideadata.annotation.Entity;
import com.ideamoment.ideadata.description.EntityDescription;
import com.ideamoment.ideadata.description.EntityDescriptionFactory;
import com.ideamoment.ideadata.description.IdDescription;
import com.ideamoment.ideadata.description.PropertyDescription;
import com.ideamoment.ideadata.description.TestGen;
import com.ideamoment.ideajdbc.IdeaJdbc;
import com.ideamoment.ideajdbc.tool.mysql.MySqlToolMapper;

/**
 * 根据Entity配置生成DDL语句
 * 
 * @author Chinakite
 *
 */
public class MySqlEntity2Ddl {
	
	private static Logger logger = LoggerFactory.getLogger(MySqlEntity2Ddl.class);
	
	private static String ENGINE = "InnoDB";
	private static String CHARSET = "utf8";
	private static String COLLATE = "utf8_unicode_ci";
	
	public void scanAndSyncTables(String basePacageName,String dbName, String catalog, String schemaPattern, boolean dropWhenExist ){
		Set<Class<?>> classes=getAllEntityClasses(basePacageName);
		if(classes!=null&&classes.size()!=0){
			for(Class<?> entityClass:classes){
				syncTable(dbName,catalog, schemaPattern,entityClass,dropWhenExist);
			}
			
		}
	}
	public Set<Class<?>> getAllEntityClasses(String basePacageName){
		Set<Class<?>> classes=new HashSet<Class<?>>();
		String[] folderArr=basePacageName.split("\\.");
		List<String> folderList=new LinkedList<String>();
		for(String folderName:folderArr){
			folderList.add(folderName);
		}
		String packageName = basePacageName;
        if (packageName.endsWith(".")) {
            packageName = packageName
                    .substring(0, packageName.lastIndexOf('.'));
        }
        getTargetPath("", folderList, "", false, classes);
       
        return classes;
		
	}
	public void getTargetPath(String currentPath,List<String> pathList,String packageName,boolean searchingStatus,Set<Class<?>> classes){
		if(searchingStatus){
			String targetFolder=pathList.get(0);
			List<File> fileList=getFiles(currentPath);
			List<String> pathListCopy;
		    for(File file:fileList){
		    	pathListCopy=new ArrayList<String>();
		    	pathListCopy.addAll(pathList);
		    	if(file.isDirectory()){
		    	   String fileName=file.getName();
				   if(isMatchPattern(targetFolder, fileName)){
					   pathListCopy.remove(0);
					   String newPackageName=packageName+"."+fileName;
					   if(pathListCopy.size()==0){
						   scanPackage(file.getPath(), newPackageName, classes);
						   logger.info("Found matched package......"+newPackageName);
					   }else{
						   searchingStatus=false;
						   getTargetPath(file.getPath(), pathListCopy, newPackageName, searchingStatus, classes);
					   }
					   
				   }
				   pathListCopy=new ArrayList<String>();
			       pathListCopy.addAll(pathList);
				   String newPackageName=packageName+"."+fileName;
				   getTargetPath(file.getPath(), pathListCopy, newPackageName, true, classes);
				   
		    	}
			   
			}
			
			
		}else{
			String curPath=pathList.remove(0);
			boolean isReachedBottom=false;
			if(pathList.size()==0){
				isReachedBottom=true;
			}
	       if(isMatchTwoStars(curPath)){
	    	   String path;
	    	   if(packageName.equals("")){
	    		   URL file=this.getClass().getResource("/");
		    	   path=file.getPath();
	    	   }else{
	    		   path=currentPath;
	    	   }
	    	   if(isReachedBottom){
	    		   String finalPath=path;
	    		   String newPackageName=packageName;
	    		   scanPackage(finalPath, newPackageName, classes);
	    		   logger.info("Found matched package......"+newPackageName);
	    	   }else {
	    		   List<File> fList=getFiles(path);
	    		   searchingStatus=true;
	    		   List<String> pathListCopy;
	    		   for(File dir:fList){
	    			   if(dir.isDirectory()){
	    				   pathListCopy=new ArrayList<String>();
		    			   pathListCopy.addAll(pathList);
		    			   String newPackageName=packageName;
		    			   if(!packageName.equals("")){
		    				   newPackageName=newPackageName+".";
		    			   }
		    			   newPackageName=newPackageName+dir.getName();
		    			   getTargetPath(dir.getPath(), pathListCopy, newPackageName, searchingStatus,classes);
	    			   }
	    			   
	    			   
	    		   }
			  }
	       }else{
	    	   if(packageName.equals("")){
	    		   URL file=this.getClass().getResource("/");
		    	   String path=file.getPath();
	    		   currentPath=path;
	    	   }
	    	   List<File> fList=getFiles(currentPath);
	    	   List<String> pathListCopy;
	    	   for(File dir:fList){
	    		   if(dir.isDirectory()){
	    			   pathListCopy=new ArrayList<String>();
	    			   pathListCopy.addAll(pathList);
	    			   if(isMatchPattern(curPath, dir.getName())){
	    				   String newPackName=packageName;
	    				   if(!newPackName.equals("")){
	    					   newPackName=packageName+".";
		    			   }
	    				   newPackName=newPackName+dir.getName();
				    	   if(isReachedBottom){
				    		   scanPackage(dir.getPath(), newPackName, classes);
				    		   logger.info("Found matched package......"+newPackName);
				    	   }else{
				    		   getTargetPath(dir.getPath(), pathListCopy, newPackName, searchingStatus, classes);
				    	   }
				       }
	    		   }
	    	   }
	    	   
	       } 
		}
	}
	public boolean isMatchPattern(String name,String target){
		if (!isMatchOneStar(name)) {
			return name.equals(target);
		}else{
			String patternStr=name.replace("*", "[\\d\\D]*");
			Pattern pattern=Pattern.compile(patternStr);
			Matcher matcher=pattern.matcher(target);
			boolean b=matcher.matches();
			return b;
		}
	}
	public boolean isMatchTwoStars(String name){
		return name.contains("**");
	}
	public boolean isMatchOneStar(String name){
		return name.contains("*")&&!name.contains("**");
	}
	public List<File> getFiles(String path){
		File dir=new File(path);
		if(!dir.exists()){
			return null;
		}
		File[] files=dir.listFiles();
		List<File> fileList=new ArrayList<File>();
		for (File file:files) {
			fileList.add(file);
		}
		return fileList;
	}
	
	public void scanPackage(String filePath,String packageName,Set<Class<?>> classes){ //得到要扫描的包的最顶层后，递归得到所有的classes
    	File dir=new File(filePath);
    	if(!dir.exists()){
    		return;
    	}
    	if(!packageName.equals("")){
    		packageName=packageName+".";
    	}
    	File[] files=dir.listFiles();
    	for(File file:files){
    		if(file.isDirectory()){
    			scanPackage(file.getPath(),packageName+file.getName(),classes);
    		}else{
    			try {
    				int index=file.getName().lastIndexOf(".class");
    				if(index!=-1){
    					String className=file.getName().substring(0,index);
        				Class<?> currentClass=Class.forName(packageName+className);
        				if(currentClass.isAnnotationPresent(Entity.class)){
        					classes.add(currentClass);
        					logger.info("Found entityclass......  :"+currentClass);
        				}
    				}
				} catch (ClassNotFoundException e) {
					e.printStackTrace();
				}
    		}
    	}
    	
    	
    }
	public void syncTable(String dbName, String catalog, String schemaPattern, Class entityClass, boolean dropWhenExist) {
		EntityDescription entityDesc = EntityDescriptionFactory.getInstance().getEntityDescription(entityClass);
		Connection conn = IdeaJdbc.db(dbName).beginTransaction().getConnection();
		
		try {
			DatabaseMetaData dbMeta = conn.getMetaData();
			ResultSet tableRs = dbMeta.getTables(catalog, schemaPattern, entityDesc.getDataSet(), new String[]{"TABLE"});
			
			if(tableRs.next()) {
				if(dropWhenExist) {		//删除重建
					logger.info("Table [{}] is exists, it will be droped.", new Object[]{entityDesc.getDataSet()});
					dropTable(entityDesc, conn);
					logger.info("Table [{}] is droped.", new Object[]{entityDesc.getDataSet()});
					createTable(entityDesc, conn);
					logger.info("Table [{}] is created.", new Object[]{entityDesc.getDataSet()});
				}else{		//修改表，目前只支持增加列，不支持修改和删除列，因为有许多不可控的数据因素要处理，TODO.
					logger.info("Table [{}] is exists, it will be altered.", new Object[]{entityDesc.getDataSet()});
					alterTable(entityDesc, conn, dbMeta, catalog, schemaPattern);
					logger.info("Table [{}] is altered.", new Object[]{entityDesc.getDataSet()});
				}
			}else{
				logger.info("Table [{}] is not exists, it will be created.", new Object[]{entityDesc.getDataSet()});
				createTable(entityDesc, conn);
				logger.info("Table [{}] is created.", new Object[]{entityDesc.getDataSet()});
			}
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			IdeaJdbc.db(dbName).endTransaction();
			conn = null;
		}
		
	}
	
	private void alterTable(EntityDescription entityDesc, Connection conn, DatabaseMetaData dbMeta, String catalog, String schemaPattern) throws SQLException {
		HashSet<String> existsColumns = getAllExistsColumns(entityDesc, conn, dbMeta, catalog, schemaPattern);
		
		List<PropertyDescription> addPropertyDescriptions = new ArrayList<PropertyDescription>();
		boolean isAddId = false;
		if(entityDesc.hasId() || entityDesc.hasProperty()) {
			IdDescription idDescription = entityDesc.getIdDescription();
			
			if(!(existsColumns.contains(idDescription.getDataItem().toUpperCase()))) {
				addPropertyDescriptions.add(idDescription);
				isAddId = true;
			}
			
			List<PropertyDescription> propertyDescriptions = entityDesc.getProperties();
			for(PropertyDescription propDesc : propertyDescriptions) {
				if(!(existsColumns.contains(propDesc.getDataItem().toUpperCase()))) {
					addPropertyDescriptions.add(propDesc);
				}
			}
			
			if(addPropertyDescriptions.size() > 0) {
				StringBuffer sb = new StringBuffer();
				sb.append("ALTER TABLE " + entityDesc.getDataSet());
				int i=0;
				for(PropertyDescription propDesc : addPropertyDescriptions) {
					if(i > 0) {
						sb.append(",");
					}
					sb.append(" ADD COLUMN ");
					if(propDesc instanceof IdDescription) {
						sb.append(generateIdColumnSql((IdDescription)propDesc));
					}else{
						sb.append(generateColumnSql(propDesc));
					}
					i++;
				}
				sb.append(";");
				conn.createStatement().execute(sb.toString());
				
				if(isAddId) {
					conn.createStatement().execute(generateAddPKSql(entityDesc));
				}
			}
		}
	}
	
	private void createTable(EntityDescription entityDesc, Connection conn) throws SQLException{
		String sql = generateCreateTableSql(entityDesc);
		conn.createStatement().execute(sql);
	}
	
	private void dropTable(EntityDescription entityDesc, Connection conn) throws SQLException{
		String sql = generateDropTableSql(entityDesc);
		conn.createStatement().execute(sql);
	}
	
	private HashSet<String> getAllExistsColumns(EntityDescription entityDesc, Connection conn, DatabaseMetaData dbMeta, String catalog, String schemaPattern) throws SQLException {
		HashSet<String> existsColumns = new HashSet<String>();
		
		ResultSet rs = dbMeta.getColumns(catalog, schemaPattern, entityDesc.getDataSet(), "%");
		while(rs.next()) {
			String columnName = rs.getString("COLUMN_NAME");
			existsColumns.add(columnName.toUpperCase());
		}
		
		return existsColumns;
	}
	
	private String generateDropTableSql(EntityDescription entityDescription) {
		StringBuffer sb = new StringBuffer();
		sb.append("DROP TABLE ").append(entityDescription.getDataSet()).append(";");
		return sb.toString();
	} 
	
	private String generateCreateTableSql(EntityDescription entityDescription) {
		assert entityDescription  != null;
		
		if(entityDescription.hasId() || entityDescription.hasProperty()) {
			StringBuffer sb = new StringBuffer();
			sb.append("CREATE TABLE ")
			  .append(entityDescription.getDataSet())
			  .append(" (");
			
			IdDescription idDescription = entityDescription.getIdDescription();
			List<PropertyDescription> propertyDescriptions = entityDescription.getProperties();
			
			int i=0;
			if(entityDescription.hasId()) {
				sb.append(generateIdColumnSql(idDescription));
				i++;
			}
			
			if(entityDescription.hasProperty()) {
				for(PropertyDescription propertyDesc : propertyDescriptions) {
					if(i > 0) {
						sb.append(",");
					}
					sb.append(generateColumnSql(propertyDesc));
					i++;
				}
			}
			
			if(entityDescription.hasId()) {
				if(i > 0) {
					sb.append(",");
				}
				sb.append(generatePKSql(idDescription));
			}
			
			sb.append(" )");
			sb.append(" ENGINE=").append(ENGINE)
			  .append(" DEFAULT CHARSET=").append(CHARSET)
			  .append(" COLLATE=").append(COLLATE)
			  .append(";");
			
			return sb.toString();
		}
		
		return null;
	}
	
	private String generateAddPKSql(EntityDescription entityDesc) {
		StringBuffer sb = new StringBuffer();
		sb.append("ALTER TABLE ").append(entityDesc.getDataSet()).append(" ADD PRIMARY KEY (").append(entityDesc.getIdDescription().getDataItem()).append(");");
		return sb.toString();
	}
	
	private String generateIdColumnSql(IdDescription idDesc) {
		StringBuffer sb = new StringBuffer();
		
		String dataItem = idDesc.getDataItem();
		DataItemType dataItemType = idDesc.getType();
		
		int length = idDesc.getLength();
		if(length == -1) {
			length = MySqlToolMapper.lengthMapper.get(dataItemType);
		}
		
		sb.append(" ").append(dataItem)
		  .append(" ").append(MySqlToolMapper.typeMapper.get(dataItemType));
		
		if(length != -1) {
			sb.append("(").append(length).append(")");
		}
		
		sb.append(" NOT NULL");
		
		return sb.toString();
	}
	
	private String generatePKSql(IdDescription idDesc) {
		StringBuffer sb = new StringBuffer();
		sb.append("PRIMARY KEY (").append(idDesc.getDataItem()).append(")");
		return sb.toString();
	}
	
	private String generateColumnSql(PropertyDescription propertyDesc) {
		StringBuffer sb = new StringBuffer();
		
		String dataItem = propertyDesc.getDataItem();
		DataItemType dataItemType = propertyDesc.getType();
		
		int length = propertyDesc.getLength();
		
		if(length == -1) {
			length = MySqlToolMapper.lengthMapper.get(dataItemType);
		}
		
		sb.append(" ").append(dataItem)
		  .append(" ").append(MySqlToolMapper.typeMapper.get(dataItemType));
		
		if(length != -1) {
			sb.append("(").append(length).append(")");
		}
		
		if(!propertyDesc.isNullable()) {
			sb.append(" NOT NULL");
		}
		
		String defaultValue = propertyDesc.getDefaultValue();
		if(defaultValue != null && defaultValue.trim().length() > 0) {
			if(dataItemType == DataItemType.BIGINT 
					|| dataItemType == DataItemType.DECIMAL
					|| dataItemType == DataItemType.DOUBLE
					|| dataItemType == DataItemType.FLOAT
					|| dataItemType == DataItemType.INT
					|| dataItemType == DataItemType.INTEGER
					|| dataItemType == DataItemType.LONG
					|| dataItemType == DataItemType.NUMERIC
					|| dataItemType == DataItemType.SHORT
					|| dataItemType == DataItemType.SMALLINT
					) {
				sb.append(" DEFAULT ").append(defaultValue);
			}else if(dataItemType == DataItemType.BOOLEAN) {
				sb.append(" DEFAULT ").append(defaultValue);
			}else{
				sb.append(" DEFAULT ").append("'").append(defaultValue).append("'");
			}
		}else{
			if(propertyDesc.isNullable()) {
				sb.append(" DEFAULT NULL");
			}
		}
		
		return sb.toString();
	}
	
	
	public static void main(String[] args) {
		MySqlEntity2Ddl m = new MySqlEntity2Ddl();
		Set<Class<?>> set=m.getAllEntityClasses("com.ideamoment.ideadata.*");
		m.getAllEntityClasses("com.**.idea*.tool");
		m.getAllEntityClasses("**.tool");
		m.syncTable("mysql", "ideajdbc", null, TestGen.class, false);
	}
}
