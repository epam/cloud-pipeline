if command -v "yum" >/dev/null 2>&1 ; then
	yum install -y 	wget \
				curl \
				python \
				nano \
				vim \
				gedit \
				nedit \
				git
else
	apt-get update && \
	apt-get install -y 	wget \
				curl \
				python \
				nano \
				vim \
				gedit \
				git
fi