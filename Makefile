JAVAC  = /home/wizoutsugar/BurpSuitePro/jre/bin/javac
BURP   = libs/burp-extender-api-1.7.22.jar
SRC    = $(shell find src -name "*.java")
OUT    = out/classes
JAR    = spectra.jar

$(JAR): $(SRC)
	mkdir -p $(OUT)
	$(JAVAC) --release 11 -cp $(BURP) -d $(OUT) $(SRC)
	cd $(OUT) && zip -r ../../$(JAR) .
	@echo "Built $(JAR)"



clean:
	rm -rf out $(JAR)

install: $(JAR)
	cp $(JAR) ~/.BurpSuite/extensions/

.PHONY: clean install
