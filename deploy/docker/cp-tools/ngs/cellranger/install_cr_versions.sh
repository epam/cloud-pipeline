_CELLRANGER_URL="$1"
_CELLRANGER_HOME="$2"

if [ -z "$_CELLRANGER_URL" ]; then
    echo "CELLRANGER_URL is not set. Specify it using \"--build-arg CELLRANGER_URL=http://\". Valid temporary distr URL can be obtained from https://support.10xgenomics.com/single-cell-gene-expression/software/downloads/latest"
    exit 1
fi

if [ -z "$_CELLRANGER_HOME" ]; then
    _CELLRANGER_HOME="/opt/cellranger"
    echo "CELLRANGER_HOME not set, defaulting to $_CELLRANGER_HOME"
fi

mkdir -p $_CELLRANGER_HOME
CELLRANGER_URL_LIST=($CELLRANGER_URL)

cd /tmp
for CELLRANGER_URL_ITEM in "${CELLRANGER_URL_LIST[@]}"; do
    wget -q -O cellranger.tar.gz "${CELLRANGER_URL_ITEM}"
    tar -zxvf cellranger.tar.gz
    rm -rf cellranger.tar.gz
    \cp /tmp/sge.template cellranger-*/martian-cs/*/jobmanagers/
    mv cellranger-* $CELLRANGER_HOME/
done
rm -f /tmp/sge.template
chmod 0777 -R "$CELLRANGER_HOME"
cd $CELLRANGER_HOME 


# Setup latest version
_LATEST_HOME=$_CELLRANGER_HOME/cellranger-latest
rm -rf $_LATEST_HOME
_LATEST_VERSION=$(ls -d $_CELLRANGER_HOME/cellranger-*/ | sort -r | head -n 1)
if [ -z "$_LATEST_VERSION" ]; then
    echo "Unable to determine latest version of $_CELLRANGER_HOME, skipping it"
    exit 0
fi
ln -s $_LATEST_VERSION $_LATEST_HOME

echo "$_LATEST_VERSION is set as latest version and symlinked to $_LATEST_HOME"
