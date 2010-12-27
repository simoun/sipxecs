AC_DEFUN([SF_MONGOCLIENT],
[
  AC_ARG_WITH([mongoclient],
    AS_HELP_STRING([--with-mongoclient@<:@=DIR@:>@], [use mongoclient (default is yes) - it is possible to specify the root directory for mongoclient (optional)]),
    [
    if test "${withval}" = "no"; then
      want_mongo="no"
    elif test "${withval}" = "yes"; then
      want_mongo="yes"
      ac_mongo_path=""
    else
       want_mongo="yes"
       ac_mongo_path="${withval}"
    fi
    ],
    [want_mongo="yes"])

  if test "${want_mongo}" = "yes"; then

    MONGO_CLIENT_CFLAGS=""
    MONGO_CLIENT_LDFLAGS=""
  
    if test "${ac_mongo_path}" != ""; then
      ac_mongo_include_path="${ac_mongo_path}/include"
      ac_mongo_lib_path="${ac_mongo_path}/lib64 ${ac_mongo_path}/lib"
    else 
      ac_mongo_include_path="/opt/mongo/include /usr/local/include /usr/include"
      ac_mongo_lib_path="/opt/mongo/lib64 /opt/mongo/lib /usr/local/lib64  /usr/local/lib  /usr/lib64 /usr/lib"
    fi
  
    AC_MSG_CHECKING([for mongodb client headers in ${ac_mongo_include_path}])
    for d in ${ac_mongo_include_path}; do
      if test -f "${d}/mongo/client/dbclient.h"; then
        MONGO_CLIENT_CFLAGS="-I${d}/mongo"
        found_inc="yes"
        break
      fi
    done
  
    if test "${found_inc}" != "yes"; then
        AC_MSG_RESULT(no)
    else
        AC_MSG_RESULT(yes)
    fi
  
    AC_MSG_CHECKING([for mongodb client shared library in ${ac_mongo_lib_path}])
    for d in ${ac_mongo_lib_path}; do
      if test -f "${d}/libmongoclient.so"; then
        MONGO_CLIENT_LDFLAGS="-L${d} -lmongoclient"
        found_lib="yes"
        break
      fi
    done
  
    if test "${found_lib}" != "yes"; then
        AC_MSG_RESULT(no)
    else
        AC_MSG_RESULT(yes)
    fi

    # TODO: check min version and compile test
  	
    AC_SUBST(MONGO_CLIENT_CFLAGS)
    AC_SUBST(MONGO_CLIENT_LDFLAGS)
  fi
])
