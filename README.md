# Natchez Trace

[![Discord](https://img.shields.io/discord/632277896739946517.svg?label=&logo=discord&logoColor=ffffff&color=404244&labelColor=6A7EC2)](https://discord.gg/CSh8u9yPMe)
[![Join the chat at https://gitter.im/skunk-pg/Lobby](https://badges.gitter.im/skunk-pg/Lobby.svg)](https://gitter.im/tpolecat/natchez?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Maven Central](https://img.shields.io/maven-central/v/org.tpolecat/natchez-core_2.13.svg)](https://maven-badges.herokuapp.com/maven-central/org.tpolecat/natchez-core_2.13)
[![Javadocs](https://javadoc.io/badge/org.tpolecat/natchez-core_2.13.svg)](https://javadoc.io/doc/org.tpolecat/natchez-core_2.13)

Natchez is distributed tracing library for Scala.

Please proceed to the [microsite](https://typelevel.org/natchez/) for more information.


#### AWS Cloud Watch Log Group Name Configuration

To add an AWS Cloud Watch log group name to the traces, add the `aws_group_name` key in the annotation with the value as log group name.