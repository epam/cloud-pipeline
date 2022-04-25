Data Storage:
- /common/opt/R/site_lib/RLib_Common
- RLib_Common
- /opt/R/site_lib/RLib_Common
Data Storage:
- /common/opt/R/site_lib/RLib_Minimal
- RLib_Minimal
- /opt/R/site_lib/RLib_Minimal
 ####
mkdir /R
mount 10.0.2.69:/common/opt/R /R
mkdir -p /R/site_lib/RLib_Common/3.5.3
mkdir -p /R/site_lib/RLib_Common/3.6.3
mkdir -p /R/site_lib/RLib_Common/4.0.0
mkdir -p /R/site_lib/RLib_Minimal/3.5.3
mkdir -p /R/site_lib/RLib_Minimal/3.6.3
mkdir -p /R/site_lib/RLib_Minimal/4.0.0

R_HOME=$(R RHOME)
R_VERSION=$(Rscript -e "cat(strsplit(version[['version.string']], ' ')[[1]][3])")
MOUNT_POINT=/opt/R/site_lib/RLib_Common
mkdir -p $MOUNT_POINT
mount 10.0.2.69:/common/opt/R/site_lib/RLib_Common $MOUNT_POINT
mkdir -p "$R_HOME/site-library"
mount -B "${MOUNT_POINT}/${R_VERSION}" "$R_HOME/site-library"

mkdir -p /cloud-home/E0383928
R -e "install.packages('stringi', repos = 'http://cran.us.r-project.org', lib='$R_HOME/site-library', configure.args=c('--disable-cxx11'))"
R -e "install.packages('devtools', repos = 'http://cran.us.r-project.org', lib='$R_HOME/site-library', configure.args=c('--disable-cxx11'))"
R -e "install.packages('BiocManager', repos = 'http://cran.us.r-project.org', lib='$R_HOME/site-library')"
R -e "BiocManager::install(ask = FALSE, lib='$R_HOME/site-library')"

cd ~
cat > install_package.R <<EOF
args = commandArgs(trailingOnly=TRUE)
source = args[1]
pkg = args[2]
version = args[3]
site_pkg_path = paste(R.home(), 'site-library', sep='/')
if (!pkg %in% rownames(installed.packages())) {
    if (source == 'bioc') {
        BiocManager::install(pkg, update = FALSE, lib=site_pkg_path)
    } else if (source == 'cran') {
        if (!is.null(version)) {
            devtools::install_version(pkg, version=version, repos ="http://cran.us.r-project.org", lib=site_pkg_path, upgrade='never')
        } else {
            install.packages(pkg, repos = 'http://cran.us.r-project.org', lib=site_pkg_path)
        }
    } else {
        cat(paste('Unknown source', source, sep=' '), '\n')
    }
} else {
    cat(paste(pkg, 'is already installed', sep=' '), '\n')
}
EOF

# NA 3.5.3 Rscript install_package.R cran glmnet 2.0-18
# NA 3.5.3 Rscript install_package.R cran latticeExtra 0.6-28
# NA 3.5.3 Rscript install_package.R bioc BloodCancerMultiOmics2017 && \
# NA 3.5.3 Rscript install_package.R cran gRbase 1.8-3
# NA 3.5.3 Rscript install_package.R cran caTools 1.17
# NA 3.5.3 Rscript install_package.R cran ggpmisc 0.3.4
# NA 3.5.3 Rscript install_package.R cran ffbase 0.11
# NA 3.5.3 Rscript install_package.R bioc CEMiTool && \

export PKG_CXXFLAGS="-std=c++11"
export MAKE="make -j$(nproc)"
Rscript install_package.R bioc annotate && \
Rscript install_package.R bioc AnnotationDbi && \
Rscript install_package.R bioc AnnotationHub && \
Rscript install_package.R bioc Biobase && \
Rscript install_package.R bioc BiocGenerics && \
Rscript install_package.R bioc biomaRt && \
Rscript install_package.R bioc BloodCancerMultiOmics2017 && \
Rscript install_package.R bioc CEMiTool && \
Rscript install_package.R bioc clusterProfiler && \
Rscript install_package.R bioc ComplexHeatmap && \
Rscript install_package.R bioc DESeq2 && \
Rscript install_package.R bioc DOSE && \
Rscript install_package.R bioc edgeR && \
Rscript install_package.R bioc ensembldb && \
Rscript install_package.R bioc enrichplot && \
Rscript install_package.R bioc fgsea && \
Rscript install_package.R bioc gage && \
Rscript install_package.R bioc genefilter && \
Rscript install_package.R bioc GSEABase && \
Rscript install_package.R bioc GSVA && \
Rscript install_package.R bioc GSVAdata && \
Rscript install_package.R bioc graph && \
Rscript install_package.R bioc IRanges && \
Rscript install_package.R bioc KEGGdzPathwaysGEO && \
Rscript install_package.R bioc MOFA2 && \
Rscript install_package.R bioc MultiAssayExperiment && \
Rscript install_package.R bioc limma && \
Rscript install_package.R bioc OmnipathR && \
Rscript install_package.R bioc org.Hs.eg.db && \
Rscript install_package.R bioc org.Mm.eg.db && \
Rscript install_package.R bioc omicade4 && \
Rscript install_package.R bioc PADOG && \
Rscript install_package.R bioc pathview && \
Rscript install_package.R bioc QuSAGE && \
Rscript install_package.R bioc ReactomeGSA && \
Rscript install_package.R bioc sva && \
Rscript install_package.R bioc S4Vectors && \
Rscript install_package.R bioc tximport && \
Rscript install_package.R bioc tximeta && \
Rscript install_package.R bioc vsn

Rscript install_package.R cran doParallel && \
Rscript install_package.R cran data.table && \
Rscript install_package.R cran egg && \
Rscript install_package.R cran gridExtra && \
Rscript install_package.R cran ggfortify && \
Rscript install_package.R cran ggrepel && \
Rscript install_package.R cran gplots && \
Rscript install_package.R cran igraph && \
Rscript install_package.R cran IntNMF && \
Rscript install_package.R cran msigdbr && \
Rscript install_package.R cran pheatmap && \
Rscript install_package.R cran RColorBrewer && \
Rscript install_package.R cran r.jive && \
Rscript install_package.R cran tidyverse && \
Rscript install_package.R cran styler && \
Rscript install_package.R cran survival && \
Rscript install_package.R cran survminer && \
Rscript install_package.R cran SPARQL && \
Rscript install_package.R cran UpSetR && \
Rscript install_package.R cran WGCNA && \
Rscript install_package.R cran VennDiagram && \
Rscript install_package.R cran zoo
