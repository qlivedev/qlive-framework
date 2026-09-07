---
title: QLive
description: Documentation for building applications on the QLive framework.
template: splash
sidebar:
  hidden: true
hero:
  tagline: A full-stack framework whose pages arrive with their data already in them.
  actions:
    - text: Read the overview
      link: ./overview/
      icon: right-arrow
    - text: View on GitHub
      link: https://github.com/quinscape/qlive-framework
      icon: external
      variant: minimal
---

## What this is

Documentation for the **framework user** -- someone building an
application on QLive, not someone working on QLive itself.

Internal development documentation stays in `docs/` in the repository:
design sketches for ideas not yet realized, and notes aimed at
maintainers. Nothing here is for maintainers; where a page explains how
QLive works internally, it is because an application author has to know
it.

Start with the [Overview](./overview/), which explains what the framework
does and how a page reaches the browser. The rest follows the order of
the sidebar.

## Not here yet

A second half of the framework-user documentation does not live here:
what gets generated into a new application alongside the template
extracted from `qlive-test`. Some of it will be inherited from these
pages -- the pnpm and Maven setup, the dev loop -- and some will be
specific to the generated application. That half waits for the
templating command.

Styling is documented in
[`docs/styling.md`](https://github.com/quinscape/qlive-framework/blob/main/docs/styling.md)
for the moment. It reads as framework-user documentation and is a
candidate to move here, but it has not been moved.

## Status

Written against the repository as of 2026-09-07. QLive is pre-release:
nothing is published to a registry yet, and `qlive-test` is both the
integration test target and the source the application template will be
extracted from. Where a page describes something that is not settled, it
says so rather than inventing a story.
