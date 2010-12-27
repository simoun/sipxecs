
#ifndef IdentityDB_h_
#define IdentityDB_h_

#include <string>
#include "os/OsMutex.h"
#include "utl/UtlString.h"
#include "sipdb/ResultSet.h"
#include "net/Url.h"

namespace mongo {
  class DBClientConnection;
}

class IdentityDB
{
public:
  IdentityDB();

  void setServer(const std::string& server);
  
  void setNamespace(const std::string& namespce);

  void release();

  void init();

  // Query the user ids associated with a particular permission
  void getPermissions(const Url& identity, ResultSet& rResultset) const;

  // Query the user ids associated with a particular permission
  void getIdentities(const UtlString& permission, ResultSet& rResultset) const;

  virtual ~IdentityDB();

private:
  std::string m_server;
  std::string m_namespace;
  mongo::DBClientConnection* m_connection;
  static UtlString gIdentityKey;
  static UtlString gPermissionKey;
};

#endif
