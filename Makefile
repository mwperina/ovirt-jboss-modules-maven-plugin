#
# Copyright oVirt Authors
# SPDX-License-Identifier: Apache-2.0
#

all: rpm

PWD=$(shell bash -c "pwd -P")
pomversion=$(shell $(PWD)/version.py --pom)
rpmversion=$(shell $(PWD)/version.py --rpm)
rpmdist=$(shell rpm --eval '%dist')
rpmrelease=0.1$(rpmsuffix)$(rpmdist)

RPMTOP=$(PWD)/rpmtop
NAME=ovirt-jboss-modules-maven-plugin
SPEC=$(NAME).spec

TARBALL=$(NAME)-$(pomversion).tar.gz
SRPM=$(RPMTOP)/SRPMS/$(NAME)-$(rpmversion)-$(rpmrelease).src.rpm

.PHONY:
spec: ovirt-jboss-modules-maven-plugin.spec.in
	sed \
		-e 's/@POM_VERSION@/$(pomversion)/g' \
		-e 's/@RPM_VERSION@/$(rpmversion)/g' \
		-e 's/@RPM_RELEASE@/$(rpmrelease)/g' \
		-e 's/@TARBALL@/$(TARBALL)/g' \
		< $< \
		> $(SPEC)

.PHONY: tarball
tarball: spec
	git ls-files | tar --transform='s|^|$(NAME)/|' --files-from /proc/self/fd/0 -czf $(TARBALL) $(SPEC)

.PHONY: srpm
srpm: tarball
	rpmbuild \
		--define="_topdir $(RPMTOP)" \
		-ts $(TARBALL)

.PHONY: rpm
rpm: srpm
	rpmbuild \
	--define="_topdir $(RPMTOP)" \
	--rebuild $(SRPM)

.PHONY: clean
clean:
	$(RM) $(NAME)*.tar.gz $(SPEC)
	$(RM) -r rpmtop
