mongo_SRPM = mongodb-1.6.4-3.fc15.src.rpm

# targets not defined, nothing to do
mongo.autoreconf mongo.configure mongo.dist:;

mongo.srpm :
	$(call CopySourceFile, $(mongo_SRPM), $(MOCK_SRPM_DIR))

