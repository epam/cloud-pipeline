function isRedirectStatus(status: number): boolean {
    return status >= 300 && status < 400;
}
export async function fetchWithHooks(
    url: string | URL,
    options: RequestInit = {},
    onRedirect?: (response: Response, from: string | URL, to: URL, step: number) => boolean,
    maxRedirects: number = 10
) {
    let currentUrl = url;

    for (let step = 0; step < maxRedirects; step++) {
        const response = await fetch(currentUrl, {
            ...options,
            redirect: "manual",
        });

        if (isRedirectStatus(response.status)) {
            const location = response.headers.get("Location");
            if (!location)
                return response;
            const nextUrl = new URL(location, currentUrl);
            const decision = onRedirect?.(response, currentUrl, nextUrl, step);
            if (!decision)
                return response;
            currentUrl = nextUrl;
        } else {
            return response;
        }
    }

    throw new Error("Too many redirects");
}