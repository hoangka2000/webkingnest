from pydantic import AliasGenerator, BaseModel, ConfigDict, EmailStr, Field


def to_camel(string: str) -> str:
    parts = string.split("_")
    return parts[0] + "".join(word.capitalize() for word in parts[1:])


class CamelModel(BaseModel):
    model_config = ConfigDict(
        populate_by_name=True,
        alias_generator=AliasGenerator(
            validation_alias=to_camel,
            serialization_alias=to_camel,
        ),
    )


class OrderItemRequest(CamelModel):
    id: str
    quantity: int = Field(gt=0)


class CreateOrderRequest(CamelModel):
    order_code: str | None = None
    customer_name: str
    customer_email: EmailStr
    customer_phone: str
    customer_address: str
    customer_note: str | None = None
    coupon: str | None = None
    payment_method: str | None = None
    items: list[OrderItemRequest]
