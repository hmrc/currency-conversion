# currency-conversion

This service is used to retrieve one or more exchange rates for countries from the approved [HMRC monthly exchange rates](https://www.gov.uk/government/publications/hmrc-exchange-rates-for-2019-monthly "HMRC Monthly Exchange Rates")

## Flow

### Successful Flow (200 Response)
- A call is made to the `/rates/:date` endpoint with a valid date and one or more  currency codes in the query string.
- A `200` response with JSON data containing the start and end date for the rates, the currency code and the rate for that currency code.


## Endpoints
`GET /currency-conversion/rates/:date`

### Example

`GET -> /currency-conversion/rates/2019-03-21?cc=USD`

#### Response Body

```json
[
    {
        "startDate": "2019-03-01",
        "endDate": "2019-03-31",
        "currencyCode": "USD",
        "rate": "1.3064"
    }
]
```

NB: If an exchange rate file cannot be found, an error will be logged and a `WARNING 299 - Date out of range` will be in the response headers.

## Exchange Rates Data

Exchange rates data currently resides in the `/conf/resources/xml` directory. This service currently only supports [the exchange rate file data](https://www.gov.uk/government/publications/hmrc-exchange-rates-for-2019-monthly) in the [xml format](http://www.hmrc.gov.uk/softwaredevelopers/2019-exrates.html) 