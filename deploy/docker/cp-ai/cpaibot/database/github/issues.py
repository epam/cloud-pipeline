import requests
from cpaibot.common.settings import settings
from cpaibot.common.logger import CpLogger as Logger
from cpaibot.common.model.github import GithubIssue
from cpaibot.database.github.misc import github_logger
from llama_index.core import Document

default_logger = github_logger


def get_issues(
        *,
        repository_owner: str,
        repository: str,
        logger: Logger | None = None,
        page_size: int | None = 100,
        include_comments: bool = True,
        max_comments_per_issue: int = 100,
) -> list[Document]:
    if not logger:
        logger = default_logger
    if page_size is None or page_size <= 0:
        page_size = 100
    headers = {}
    if settings.GITHUB_TOKEN is not None:
        headers.update({"Authorization": f"Bearer {settings.GITHUB_TOKEN}"})
    graphql_url = "https://api.github.com/graphql"

    # Updated query to include comments
    query = """
    query($cursor: String) {
      repository(owner: "%s", name: "%s") {
        issues(first: %s, after: $cursor) {
          pageInfo {
            hasNextPage
            endCursor
          }
          nodes {
            number
            title
            body
            url
            closed
            author {
                login
            }
            comments(first: %s) {
              nodes {
                body
                author {
                  login
                }
                createdAt
                url
              }
            }
          }
        }
      }
    }
    """ % (repository_owner, repository, page_size, max_comments_per_issue if include_comments else 0)

    cursor = None
    all_issues: list[GithubIssue] = []

    pages = 0

    logger.info(f"fetching {repository} issues (page size {page_size})...")

    while True:
        logger.info(f"fetching {repository} issues: page {pages + 1}")
        pages += 1
        variables = {"cursor": cursor}
        response = requests.post(graphql_url, json={"query": query, "variables": variables}, headers=headers)
        data = response.json()

        if "errors" in data:
            logger.error(f"error fetching {repository} issues: {data['errors']}")
            break

        issues = data["data"]["repository"]["issues"]["nodes"]
        github_issues = [GithubIssue.from_graphql_object(i) for i in issues]
        github_issues = [i for i in github_issues if i is not None]
        all_issues.extend(github_issues)

        page_info = data["data"]["repository"]["issues"]["pageInfo"]
        if not page_info["hasNextPage"]:
            break
        cursor = page_info["endCursor"]
    logger.info(f"fetching {repository} issues: {len(all_issues)} issues fetched")
    return [i.to_llama_index_document() for i in all_issues]