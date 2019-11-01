FROM fedora:latest
LABEL maintainer "https://pagure.io/fedora-ci-generic-checks"
LABEL description="This container is meant to \
run generic checks against koji builds."


# Set the labels that are used for OpenShift to describe the builder image.
LABEL io.k8s.description="Fedora generic-check-worker" \
    io.k8s.display-name="Fedora:latest generic-check-worker" \
    io.openshift.tags="builder,fedora-ci" \
    # this label tells s2i where to find its mandatory scripts
    # (run, assemble, save-artifacts)
    io.openshift.s2i.scripts-url="image:///usr/libexec/s2i"

# we need dnf-plugins-core to interface with copr
RUN dnf -y install dnf-plugins-core

# enable the rpminspect copr
RUN dnf -y copr enable dcantrel/rpminspect

# Install all package requirements
RUN dnf -y install ansible \
        curl \
        file \
        findutils \
        git \
        python3-libselinux \
        python3-dnf \
        python3-devel \
        python3-pyyaml \
        rpm-build \
        rsync \
        sudo \
        wget \
        rpminspect \
        rpminspect-data-fedora \
        rpmdeplint \
        rpmlint \
        && dnf clean all


# create the workdir for jenkins
RUN mkdir -p /tmp/workspace/fedora-build-check
#RUN chmod -R 777 /workDir

# Copy the rpminspect config until a proper data package is available
COPY rpminspect-fedora.conf /tmp/

# Copy the rpminspect data tarball
COPY 20190618-rpminspect-redhat-data.tar.gz /tmp/

# Extract the rpminspect data
RUN tar -xzf /tmp/20190618-rpminspect-redhat-data.tar.gz -C /usr/share/rpminspect

# Copy the build scripts to the container
COPY run-rpminspect.sh /tmp/
COPY convert_result_json_yaml.py /tmp/

# Run the build script
# assumes that target ENVR is available as $TARGET_ENVR
ENTRYPOINT ["bash", "/tmp/run-rpminspect.sh"]
#CMD ["/usr/libexec/s2i/usage"]
