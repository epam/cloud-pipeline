# Cloud Pipeline documentation

Cloud Pipeline documentation is provided in a **Markdown** format, that could be viewed directly from GitHub or could be built into html representation

## Documentation index

The following sections are currently covered in a documentation:

- Introduction
    - [CP introduction](md/index.md)
- Release notes
    - [Release notes v.0.13](md/release_notes/v.0.13/v.0.13_-_Release_notes.md)
    - [Release notes v.0.14](md/release_notes/v.0.14/v.0.14_-_Release_notes.md)
    - [Release notes v.0.15](md/release_notes/v.0.15/v.0.15_-_Release_notes.md)
    - [Release notes v.0.16](md/release_notes/v.0.16/v.0.16_-_Release_notes.md)
- User guide
    - [Table of contents](md/manual/Cloud_Pipeline_-_Manual.md)

## Building documentation

[MkDocs](http://www.mkdocs.org/) is used to build documentation into **html** representation. So make sure that all dependencies are installed according to the [installation guide](https://www.mkdocs.org/#installation).

Once installed, obtain **Markdown** sources from GitHub:

``` bash
$ git clone https://github.com/epam/Cloud-Pipeline.git
$ cd Cloud-Pipeline/docs
```

Run build

``` bash
$ mkdocs build
```

This will create `site/` folder, containing built **html** documentation.  
To view documentation - navigate in `Cloud-Pipeline/docs/site/` folder and launch `index.html` with a web-browser.
