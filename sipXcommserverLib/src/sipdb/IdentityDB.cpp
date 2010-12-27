#include "client/dbclient.h"
#include "sipdb/IdentityDB.h"
#include <memory.h>
#include "os/OsMutex.h"
#include "utl/UtlString.h"
#include "utl/UtlHashMap.h"
#include "os/OsSysLog.h"
#include "sipdb/ResultSet.h"
#include "net/Url.h"

// Static Initializers
UtlString IdentityDB::gIdentityKey("identity");
UtlString IdentityDB::gPermissionKey("permission");

//using namespace std;

IdentityDB::IdentityDB() {
  setServer("localhost");
  setNamespace("config.permission");
}

IdentityDB::~IdentityDB() {
  release();
}

void IdentityDB::init() {
  try {
    // FIXME: Should use connection pooling?
    m_connection = new mongo::DBClientConnection();
    m_connection->connect(m_server);
    // FIXME: good place to ensure indexes
    OsSysLog::add(FAC_DB, PRI_INFO, "connected to mongodb");
  } catch(mongo::DBException &e) {
    OsSysLog::add(FAC_DB, PRI_ERR, "caught %s", e.what());
  }
}

void IdentityDB::release() {
  if (m_connection != NULL) {
    delete m_connection;
    m_connection = NULL;
  }
}

void IdentityDB::getPermissions(const Url& identity, ResultSet& results) const {
  results.destroyAll();
  UtlString identityStr;
  identity.getIdentity(identityStr);
  if (identityStr.isNull()) {
    return;
  }

  mongo::BSONObj query = BSON("identity" << identityStr.data());
  std::auto_ptr<mongo::DBClientCursor> cursor = m_connection->query(m_namespace, query);
  while (cursor->more()) {
    UtlHashMap record;
    mongo::BSONObj p = cursor->next();
    UtlString* identityValue = new UtlString(p.getStringField("identity"));
    record.insertKeyAndValue(new UtlString(gIdentityKey), identityValue);
    
    // FIXME: might be more efficient as sorted list and w/o N copies of permission that was passed in
    UtlString* permissionValue = new UtlString(p.getStringField("permission"));
    record.insertKeyAndValue(new UtlString(gPermissionKey), permissionValue);
    
    results.addValue(record);
  }
}

// potentially expensive call, could return all user ids.
void IdentityDB::getIdentities(const UtlString& permission, ResultSet& results) const {
  results.destroyAll();
  if (permission.isNull()) {
    return;
  }
  
  mongo::BSONObj query = BSON("permission" << permission.data());
  std::auto_ptr<mongo::DBClientCursor> cursor = m_connection->query(m_namespace, query);
  while (cursor->more()) {
    UtlHashMap record;
    mongo::BSONObj p = cursor->next();
    UtlString* identityValue = new UtlString(p.getStringField("identity"));
    record.insertKeyAndValue(new UtlString(gIdentityKey), identityValue);
    
    // FIXME: might be more efficient as sorted list and w/o N copies of permission that was passed in
    UtlString* permissionValue = new UtlString(p.getStringField("permission"));
    record.insertKeyAndValue(new UtlString(gPermissionKey), permissionValue);
    
    results.addValue(record);
  }
}

void IdentityDB::setServer(const std::string& server) {
  m_server = server;
}

void IdentityDB::setNamespace(const std::string& namespce) {
  m_namespace = namespce;
}

