
def parse_date(date_str):
    if date_str and not date_str.find("\\d\\d\\d\\d-\\d\\d-\\d\\d"):
        raise AttributeError("Date value is not in format yyyy-mm-dd")
    return date_str