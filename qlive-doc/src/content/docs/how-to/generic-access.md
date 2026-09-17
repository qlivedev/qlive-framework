---
title: Use generic schema types
description: Receiving object and scalars of all types
sidebar:
  order: 13
---
GraphQL forces you to define all your input and output types and dealing with all that, especially on the mutating side
can be a lot of hassle with little pay-off.

Here we'll talk about how to accept objects and scalars from the QLive domain in a generic way.

## GenericScalar

First, there is `GenericScalar` which means a scalar value of any type. 

```typescript
type GenericScalar = {
  type: string
  value: any
}
```
The `type` contains the name of the actual scalar type and `value` an object matching that scalar type. 
Note that the contained `value` is still validated against its normal validation rules.
               
The java-side scala is `de.quinscape.domainql.generic.GenericScalar` which is defined as scalar in every
QLive domain. So as soon as you use that type in any of your method objects, it will be picked up as scalar. 

## DomainObject
                                                                                                             
`DomainObject` is the same for a complete domain object of your domain.

```typescript
export type GenericDomainObject = {
  type: "DomainObject"
  value: object
}
```
The java-side scalar is `de.quinscape.domainql.generic.DomainObject`
