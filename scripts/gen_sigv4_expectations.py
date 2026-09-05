# 用 botocore(Amazon 官方 SigV4 实现)生成 SpApiSignerTest 的期望签名值。
# 密钥为 AWS 文档惯用示例值,非真实凭证;固定时间戳保证可复现。
from datetime import datetime

import botocore.auth
from botocore.auth import SigV4Auth
from botocore.awsrequest import AWSRequest
from botocore.credentials import Credentials

# 冻结 botocore 时钟:SigV4Auth 内部自己取 utcnow(),覆盖其模块引用
class _FrozenDateTime(datetime):
    @classmethod
    def utcnow(cls):
        return datetime(2026, 9, 5, 0, 0, 0)

    @classmethod
    def now(cls, tz=None):
        return datetime(2026, 9, 5, 0, 0, 0)

class _DTM:
    datetime = _FrozenDateTime
    utcnow = staticmethod(lambda: datetime(2026, 9, 5, 0, 0, 0))
    now = staticmethod(lambda tz=None: datetime(2026, 9, 5, 0, 0, 0))

    def __getattr__(self, item):
        return getattr(datetime, item)

botocore.auth.datetime = _DTM()
botocore.auth.get_current_datetime = lambda: datetime(2026, 9, 5, 0, 0, 0)

TS = "20260905T000000Z"
AK = "AKIDELECTEXAMPLE"
SK = "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY"
HOST = "sellingpartnerapi-na.amazon.com"

def sign(name, method, path, query="", headers=None, body=None, token=None,
         service="execute-api", region="us-east-1"):
    url = f"https://{HOST}{path}" + (f"?{query}" if query else "")
    h = dict(headers or {})
    if token:
        h["X-Amz-Security-Token"] = token
    req = AWSRequest(method=method, url=url, data=body, headers=h)
    req.context["timestamp"] = TS
    SigV4Auth(Credentials(AK, SK, token), service, region).add_auth(req)
    print(f"=== {name} ===")
    print("X-Amz-Date   :", req.headers.get("X-Amz-Date"))
    print("Authorization:", req.headers.get("Authorization"))
    print()

# ① 最简 GET:无查询、空 body
sign("1 vanilla GET", "GET", "/orders/v0/orders")

# ② GET 带普通查询(已按字母序)
sign("2 GET query", "GET", "/orders/v0/orders",
     query="MarketplaceIds=ATVPDKIKX0DER&MaxResults=100")

# ③ GET 查询值含冒号(ISO8601 时间,RFC3986 编码为 %3A)
sign("3 GET encoded query", "GET", "/orders/v0/orders",
     query="LastUpdatedAfter=2026-09-01T00%3A00%3A00Z&MarketplaceIds=ATVPDKIKX0DER")

# ④ POST JSON body(带 Content-Type)
sign("4 POST json", "POST", "/orders/v0/orders/123-4567890-1234567",
     headers={"Content-Type": "application/json"},
     body=b'{"marketplaceIds":["ATVPDKIKX0DER"]}')

# ⑤ GET + STS 临时凭证(x-amz-security-token 参与签名)
sign("5 GET session token", "GET", "/orders/v0/orders",
     token="AQoEXAMPLETOKEN")
