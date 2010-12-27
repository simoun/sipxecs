#include <cppunit/extensions/HelperMacros.h>
#include <cppunit/TestFixture.h>
#include "client/dbclient.h"
#include "sipxunit/TestUtilities.h"
#include "sipdb/IdentityDB.h"
#include "sipdb/ResultSet.h"

class IdentityDBTest : public CppUnit::TestFixture
{
  mongo::DBClientConnection connection;
  ResultSet results;
  IdentityDB *db;

  CPPUNIT_TEST_SUITE(IdentityDBTest);
  CPPUNIT_TEST(testGetIdentities);
  CPPUNIT_TEST(testGetPermissions);
  CPPUNIT_TEST_SUITE_END();

public:
  void setUp()
  {
    connection.connect("localhost");
    connection.dropCollection("test.IdentityDBTest");
    connection.insert("test.IdentityDBTest", BSON("permission" << "AutoAttendant" << "identity" << "joe@example.com"));
    db = new IdentityDB();
    db->setNamespace("test.IdentityDBTest");
    db->init();
  }

  void tearDown()
  {
    delete db;
  }

  void testGetIdentities()
  {
    const UtlString aa("AutoAttendant");
    db->getIdentities(aa, results);
    CPPUNIT_ASSERT_EQUAL(1, results.getSize());

    const UtlString vm("Voicemail");
    db->getIdentities(vm, results);
    CPPUNIT_ASSERT_EQUAL(0, results.getSize());
  }

  void testGetPermissions()
  {
    const Url findUrl("sip:joe@example.com");
    UtlString find;
    findUrl.getIdentity(find);
    db->getPermissions(findUrl, results);
    CPPUNIT_ASSERT_EQUAL(1, results.getSize());

    const Url noScheme("joe@example.com");
    db->getPermissions(noScheme, results);
    CPPUNIT_ASSERT_EQUAL(1, results.getSize());

    const Url wrongPerson("mary@example.com");
    db->getPermissions(wrongPerson, results);
    CPPUNIT_ASSERT_EQUAL(0, results.getSize());
  }

};

CPPUNIT_TEST_SUITE_REGISTRATION(IdentityDBTest);
