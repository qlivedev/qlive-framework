---
title: QLive
description: Documentation for building applications on the QLive framework.
template: splash
sidebar:
  hidden: true
hero:
  tagline: A full-stack GraphQL injection framework
  actions:
    - text: Read the overview
      link: /qlive-framework/overview/
      icon: right-arrow
    - text: View on GitHub
      link: https://github.com/quinscape/qlive-framework
      icon: external
      variant: minimal
---
## Introduction

QLive is a fullstack framework for running applications using React and Typescript with a Java/Spring Boot server. 

## Requirements

 * Java 25
 * pnpm
 * vite
 * React 18
 * Spring Boot
 * jOOQ

## Motivation
       
QLive is a conceptually an alternative to React Server components. There are many reasons you want to use a Java server
if only for the whole Spring Boot eco system. QLive allows components to declare the GraphQL queries they need. 
The server uses static code analysis of the Typescript code to find all invocations of e.g. the `useInjection` function.

This analysis data allows the preparation of all needed data from the server and embed the results in the first response.
React components are not hydrated in the React Server component sense but just find all the data they need already present.

It simplifies component lifecycles. Ideally it removes *all* async behavior at page load. The more complex the data, the
more request latency is saved. It simplifies testing the components. 

## Getting Started

Start with the [Overview](/qlive-framework/overview/), which explains what the framework
does and how a page reaches the browser. The rest follows the order of
the sidebar.
