# First version of a machine-readable schema for QD

## Format

We have opted for XML as the chosen format, for which an XML schema (XSD) has been designed, making it possible to
validate the correctness of schemas using standard tools. Unfortunately, performing a comprehensive XSD-based validation
seems impossible for two reasons: firstly, the schema contains elements without globally unique identifiers, and it is
impossible to "pierce" everything with link checks. Secondly, the full QD schema to be verified is built up from
multiple files.

## Schema loading - files and links

The QD schema is assembled from one or more files, each of which must be a valid XML file and should be validated
against the XSD schema independently of other files.

Files are loaded one by one, with each subsequent file holding a higher priority than the preceding one. In case there
is an error during the loading or validation of any file, the loading of the entire schema terminates with an error.

After loading each subsequent file, it is merged with the files that have already been loaded. The merging occurs at the
semantic, not syntactic level. Objects with the same names found in multiple files are updated with data from the most
recently uploaded file. Any resolvable conflicts are resolved in favor of the data loaded from the new file.

At the beginning of any file, import instructions for other files may be placed, referencing other schema files that
need to be imported. The order of processing is as follows:

1. Process imported files in the order of their appearance.
2. Process the rest of the instructions in the current file.

To prevent accidental overrides of objects, all updates must be marked as updates, not as new objects. This is done
to avoid a situation where a user's custom addition to the default schema is treated as a modification in a new
version of the same schema, with objects having identical names to those defined by the user (that we are unaware
of).

The resolution of all references between the objects (such as assigning types to record fields) and the checking of
their integrity occur only after all the files have been merged. Therefore, later files may change the meaning of the
types used in the previously loaded files.

## Overview of the object types described by the schema

The QD schema can describe the following objects, grouped into respective containers:

* `type`: identifies user-defined data types and their named copies. It enables the creation of a user-defined set of
   access methods for certain types, as well as it makes it possible to switch the representation of the `decimal` type
   across the entire schema or for its individual fields.
* `enum`: describes enumeration types that can be used to decode flag fields.
* `record`: specifies QD records used in transport formats.
* `generator`: defines a family of identical QD records differing only in names. Generators are placed in the same
   container as `record` objects but have a separate namespace.
* visibility rules (`enable` and `disable`): identify rules for enabling and disabling individual record fields in the
   given schema instance.

### Named objects and their common attributes

Almost all schema objects are named, allowing for overriding and modifying them by loading multiple files. Each
subsequent file can override or modify the already existing named objects.

* At the moment, only the `visibility` rules are not named.
* All named objects have a required `name` attribute and an optional `mode` attribute.
* The `name` attribute specifies the object's name, and all objects of the same type must be unique within the same
  file. This name is used to match objects when merging multiple files.
* The `mode` attribute can take the `new` (by default) or `update` values. This attribute specifies whether the
  described object is new (`new`) or should update an existing object of the same type and name (`update`) during file
  merging.
* If, during file merging, an object with the `mode` attribute set to `new` attempts to update an existing object, the
  schema loading fails.
* For each object with the `mode` attribute set to `update`, there must be a corresponding overridden object (from the
  previously loaded files in sequence). Otherwise, the schema loading fails.

### Processing `visibility` rules

The rules for enabling/disabling both record fields and the records themselves do not have names and are not overridden.
All rules are executed when configuring the QD core in the order in which they were loaded. The last triggered rule
determines whether the field or record is enabled or disabled.

### Documentation

Nearly all objects in the schema (not only top-level objects such as types and records, but also record fields or enum
values) can contain a nested documentation element `<doc>`. The content of this element is a user-defined text required
for documenting the nesting object. If included, it must occupy the first position in its parent element and may be
present only once.

## General structure of a single schema file

Currently, the only supported schema format is XML.

Each schema file must be a fully valid XML document conforming to
the [Extensible Markup Language (XML) 1.1 (Second Edition)](https://www.w3.org/TR/xml11/) standard and contained in the
XML namespace `https://www.dxfeed.com/datascheme`.

The file can also refer to [XML schema version 1.0](https://www.w3.org/TR/xmlschema-1/) `dxfeed-schema.xsd`
or [version 1.1](https://www.w3.org/TR/xmlschema11-1/) `dxfeed-schema-1.1.xsd`. The files are validated against this
schema even in the absence of such a link.

The schema file's root element (document element) is the `<dxfeed>` element.
A document can include multiple import directives for other files, as well as containers for types, enums, records, and
visibility rules.

Import directives must come first, and containers must follow them in the specified order. Each container may occur once
or not at all.

### Import directives

Import directives are recorded as the `<import>` element with no attributes. This element must contain only plain text,
which is interpreted as the relative URL of the parent document. The schema load fails if this URL cannot be interpreted
or if the file cannot be loaded from the provided address.

### Type container

The type container is the `<types>` element, which has no attributes and contains an arbitrary number of `<type>`
elements.

### Enum container

The enum container is the `<enums>` element, which has no attributes and contains an arbitrary number of `<enum>`
elements.

### Record container and record generators

The record container is the `<records>` element, which has no attributes and contains an arbitrary set of `<record>`
and `<generator>` elements.

### Visibility rules container

The record container is a `<visibility>` element, which has no attributes and contains an arbitrary number of `<enable>`
and `<disable>` elements in no particular order. The order of visibility rules is important.

## Object types in detail

### Types

The `<type>` element describes an alias for one of the built-in types. This allows for assigning formally independent
types to different fields and then switching between them by redefining types later. All the `<type>` elements are
placed in the `<types>` container. A type is a named object and may contain documentation. In addition to `name`, the
type has a second mandatory attribute: `base`. This attribute must contain the name of another type, whether built-in or
defined in a similar way.

After loading and merging all the schema files, it should be found out for each type which built-in type it becomes.
This is done by resolving all types one by one. If the type refers to a non-existent type or if the chain of definitions
forms a loop, the schema loading fails.

#### Built-in types

The schema supports the following built-in types:

* `byte`
* `char` - `UTF8_CHAR` in Java code.
* `short`
* `int`
* `compact_int`
* `byte_array`
* `utf_char_array`
* `tiny_decimal` - `DECIMAL` in Java code. `decimal` must be redefined as `tiny_decimal` or `wide_decimal` in the
  schema.
* `short_string`
* `time_seconds`
* `time_millis`
* `time_nanos`
* `time`
* `sequence`
* `date`
* `long`
* `wide_decimal`
* `string`
* `custom_object`
* `serial_object`

Additionally, the schema supports types that will be used in the future when generating relationships between records
and event objects:

* `time_nano_part`
* `index`
* `flags`

In the current implementation, these three additional types are aliases to `compact_int`.

### Enums

> At the moment, enums are not used in the QD Java implementation, since it does not yet have a code generator for
> events and the relationships between events and records based on a machine-readable schema.

The `<enum>` element creates a new enum type. It includes a list of possible values, each with a unique name and an
optional numeric value. If no numeric value is defined, the elements are numbered in increments of 1, starting from
the last given value. If no value is given, numbering begins at zero.

The `<enum>` elements are placed in the `<enums>` container and serve as named objects that may contain documentation.
They can include values defined by the `<value>` element. Each of these values is a named object that may contain
documentation as well.

In addition to the `name` attribute, which is required for named objects, and the optional `mode` attribute, `<value>`
can contain the optional `ord` attribute. Its value must be a non-negative integer recorded in decimal notation. This
attribute specifies the numeric value corresponding to the given enum element. If the attribute is not set, newly added
values are enumerated sequentially, starting from the previously added value with a specified `ord` attribute, or from
zero if `ord` is not set for a value.

When adding new values to an existing enum, values without `ord` are numbered as if they were initially created in the
original enum.

### Records

The `<record>` element describes one or more (in the case of regionals) records used at the QD transport layer. They are
a named object that may contain documentation and are placed in the `<records>` container. All records in
the `<records>` container form a single namespace.

The record contains a field set and an optional time (index) configuration for the "history" contract. In addition to
the `name` attribute and the optional `mode` attribute, the record supports the following attributes:

* `copyFrom`: an optional attribute that defines a template record name. See the "Creation of Records According to the
  Template" section for more information.
* `disabled`: an optional boolean-type attribute that is `false` by default. If it is `true`, the given record is not
  configured in the QD core but can be used as a template.
* `regionals`: an optional boolean-type attribute, that is `false` by default. When set to `true`, the record serves as
  a template for itself and 26 records with the suffixes `&A` through `&Z`. The list of regionals is not regulated, but
  unnecessary records can be disabled by the visibility rules.
* `eventName`: an optional record alias used with special visibility rules. If this attribute is not specified, it takes
  the same name as the parent record.
  > This attribute only exists for backward compatibility and should not be used for non-Java implementations of QD.

##### Creating records based on a template

A new record can be built based on a previously described one, which will be referred to as a "template" hereafter.

In this case, the resolution of such a link occurs during the reading of each file, and the template must already exist
when it is referenced. Updating a template record with new files does not affect other records created from the
template.

When creating records based on a template, the `disabled` attribute is not inherited. Therefore, while the template
itself can be disabled (by setting `disabled` to `true`) all records based on it are enabled by default.

It is impossible to create a record update from a template, so the `update` value of the `mode` attribute is
incompatible with the presence of the `copyFrom` attribute.

##### Fields making up time (index) of the record

Records passed on with the history contract must contain 1 or 2 designated fields for use as time (index).

These fields are specified using the `<index>` element, which may be included in the records once or not at all. The
element has two attributes, `field0` and `field1`. At least one of these attributes must be present. The values of these
attributes are the names of the fields in the record. This element can be placed before all record fields are defined
since its validation occurs after the schema loading.

If one of the `<index>` element attributes refers to a missing field the loading fails.

If one of the index fields is not specified, the QD core configuration generates a stub field with the `void` type.

#### Record fields

A new record must include one or more fields. The `update` mode records may contain no fields and can be configured to
change the attributes or index of the history contract.

Each field is specified by the `<field>` element. The field is a named object and may contain documentation.

Each field has a type. The final (built-in) type of a field is determined after reading and merging all schema files.
This ensures that a later type definition affects record fields declared before the given type definition. This enables
customizing certain types by adding a small type customization file after the default schema, without affecting the
default schema files. Additionally, it allows referring to a type that has yet to be defined when defining a field.

If some fields refer to unknown types after loading and merging all schema files, the schema loading aborts, resulting
in an error.

In addition to the `name` attribute required for named objects and the optional `mode` attribute, the `<field>` element
supports the following attributes:

* `type`: a required attribute that specifies the type of the field. This attribute must contain the name of either a
  built-in type or a type defined with the `<type>` construct. It is only required for fields with the `mode` set to
  `new`. Redefinitions in subsequent files may not have this attribute, in which case the field type remains unchanged,
  but other properties may be modified.
* `disabled`: an optional boolean-type attribute, which is `false` by default. If set to `true`, the record field is not
  configured in the QD core. Visibility rules also affect the activity of the record field, so it is not necessary to
  redefine the record only to modify this attribute.
* `compositeOnly`: an optional boolean-type attribute, which is `false` by default. If set to `true`, this field is
  configured in the QD core for records without a regional suffix (see the `regionals` attribute). This field does not
  affect fields recorded without the `regionals` attribute.
* `eventName`: an optional record alias for use with special visibility rules. If it is not specified, it takes the same
  value as the parent `eventName`. This attribute allows the field to virtually belong to an event having a different
  name than the record.
  > This attribute only exists for backward compatibility and should not be used for non-Java implementations of QD.

Each field can have multiple aliases and tags, in addition to these attributes. Moreover, fields of the `flags` type can
include descriptions of bit subfields within them.

#### Field aliases

Besides the main name, a field may have several aliases. One of the aliases is considered the main one.
> Currently, only the main alias is in use. Others are loaded from schema files but remain unused.

Field aliases are listed in the `<alias>` elements nested within the `<field>` element. Each `<alias>` element can have
three attributes:

* `name`: a required attribute that defines the alias.
* `main`: an optional boolean-type attribute. It determines if the given alias is considered the primary one. A field
  can only have one alias marked as the main one, and if multiple aliases are set to `true`, the loading fails.
* `mode`: an optional attribute that takes the `add` (by default) or `remove` values. This attribute allows removing an
  alias from an already defined field when updating a record in subsequent files.

Attempting to add an alias that already exists or delete an alias that does not exist results in an error during the
schema loading process. If none of the aliases is marked as "main", the first-defined alias is selected as the primary
one.

If the field has the main alias (i.e., at least one alias is defined), it is used as the name of the record field in
the QD core. In such a case, the field name is used as the property name of the QD record.

#### Field tags

A field can be labeled with one or more tags. Tags are used in the field visibility rules to simplify the rules by
grouping together related fields. This allows for enabling or disabling these fields through a single rule.

Field tags are listed in the `<tag>` elements nested within the `<field>` element. Each `<tag>` element has two
attributes:

* `name`: a required attribute that defines the tag.
* `mode`: an optional attribute that takes the `add` (by default) or `remove` values. This attribute makes it possible
  to remove a tag from an already-defined field when updating a record in subsequent files.
  Attempting to add an existing tag or delete a non-existing one results in an error during the schema loading process.

#### Flag bit fields

The `flags`-type fields can include the descriptions of bit subfields within them.
> At the moment, bit fields within flags are not used in the Java implementation of QD, since it does not yet have a
> code generator for events and the relationships between events and records based on a machine-readable schema.

Bit fields are defined by placing the `<bitfields>` container element within the `<field>` element. If the field type is
different from `flags`, the presence of the `<bitfields>` element leads to an error causing the failure to load the
entire schema.

* The `flags`-type field must be explicitly specified, which cannot be done via an intermediate alias of the
  `<type>`-type.
* The `<bitfields>` container includes one or more bit field definitions.
* Each bit field is defined by the `<field>` element, distinct from its parent `<field>` element.
* The bit field is a named object and may contain documentation.
  In addition to the `name` attribute required for named objects and the optional `mode` attribute, the bit field
  supports the following attributes:
* `offset`: a required attribute of a numeric type in the range from 0 to 63 (inclusive). This attribute specifies the
  offset of a bit field within a 64-bit field of the `flags` type.
* `size`: a required attribute of a numeric type in the range from 1 to 64 (inclusive). This attribute specifies the
  size of a bit field within a 64-bit field of the `flags` type.
* `disabled`: an optional boolean-type attribute (`false` by default).
  All bit fields of the same `flags`-type field must not overlap. If this rule is violated, the schema loading fails.

### Record family generators

Groups of identical records that differ only in names can be generated using record generators.

The `<generator>` element describes a single generator that creates multiple records of the same structure used at the
QD transport layer. These elements are placed in the `<records>` container. They are named objects and may contain
documentation.

Generators form a namespace separate from records and common for all generators. Each generator consists of an iterator
that defines a list of strings varying in the names of the generated records, along with one or more records acting as
templates for generation.

The generator for each template record configures as many records in the QD core as there are strings in its iterator,
modifying only the record name and preserving the structure. If a template has regionals, regional records are also
generated.

All template records within one "parent" generator form a separate namespace that does not intersect with the namespace
of simple records or with the namespaces of templates of other generators.

Currently, there are only two types of transformation applied to template names during generation: adding a string from
the iterator to the beginning or end of the name.

Additionally, a delimiter string can be set for the generator, which is added between the template name and the string
from the iterator if the iterator's string is not empty.

In addition to the `name` attribute required for named objects and the optional `mode` attribute, the `<generator>`
element supports the following attributes:

* `type`: an optional attribute for the record name transformation mode. It can take the `prefix` and `suffix` values.
  The default value is `suffix`.
* `delimiter`: an optional attribute that specifies a delimiter string for the template name and the iterator's string.
  The default value is an empty string.

#### Iterator

The iterator is defined by the `<iterator>` element, which must precede all template records.

The iterator has one optional attribute:

* `mode`: an optional attribute that specifies how this iterator should be handled when merging files. The possible
  values are
    * `new`: the default value, which is similar to the `new` value of the `mode` attribute for named objects. It
      specifies whether the described object is new (`new`) or should update an existing object of the same type and
      name
      during file merging (`update`).
    * `append`: specifies that this iterator contents should be added to existing contents of the iterator when merging
      files.
    * `replace`: specifies that this iterator's contents should replace the existing contents of the iterator when
      merging
      files.

An iterator with the `append` and `replace` values for the `mode` attribute can only be placed within a generator with
the `mode` attribute set to `update`. Otherwise, loading fails.

The strings that form a set are placed within the iterator. Each string is provided by the content of a `<value>`
element with no attributes. The string may be empty.

An iterator can contain an arbitrary number of the `<value>` elements.

#### Template records

Template records are described by the `<record>` element, which is equivalent to describing an individual record. The
only difference is that these records are placed in a separate namespace and may overlap in name (before being
transformed with strings from the iterator) with existing record names.

Another peculiarity concerns handling the `copyFrom` attribute: the search for the template record occurs within the
namespace of simple records, not within generator templates.

### Visibility rules

The `<enable>` and `<disable>` elements define instructions for enabling and disabling the configuration of individual
records and their fields in the QD core. They are designed to fine-tune a data schema based on the standard schema by
disabling unused fields, or vice versa, enabling optional fields that are typically not included.

These instructions are processed when configuring the QD core in the order they are loaded from schema files. Therefore,
the order in which they are placed within the schema files is significant.

For each record and field, all instructions from the first to the last are checked, and the last instruction that takes
effect determines the final record or field visibility. If no rule matches the given record or field, the `disabled`
attribute value of the object is used.

An enable instruction is written as the `<enable>` element, and a disable instruction is represented by the `<disable>`
element. The `<enable>` and `<disable>` elements are placed within the `<visibility>` container.

The two elements have an identical structure and support the following attributes:

* `record`: a required attribute with the Java regex format. This attribute determines which records or record fields
  this rule is applied to. The regex is compared against the full name of the record, implying the use of the prefix `^`
  and suffix `$`.
* `field`: an optional attribute with the Java regex format. If this attribute is set, the rule must be applied to
  individual record fields. If it is not specified, the rule applies to records as a whole.
* ~~`useEventName`~~: an optional boolean-type attribute (`false` by default). This attribute specifies whether the
  expression in the `record` attribute is compared to the record name or the `eventName` attribute.
  > This attribute only exists for backward compatibility and should not be used for non-Java implementations of QD.

Additionally, the visibility rule can search for matches with record fields not only by name but also by the presence or
absence of specific tags, enabling the creation of more flexible rules that do not list numerous field names.

These filters are specified by two elements: `<include-tags>` and `<exclude-tags>`. Each element can contain any number
of `<tag>` elements that define the corresponding matching conditions.

#### Operation algorithm of visibility rules

##### Visibility rule for one record

1. If the rule has the `field` attribute or one of the `include-tags` or `exclude-tags` filters, the rule does not match
   the given record and **does not affect** its visibility. Further conditions are not checked.
2.
    1. If the `useEventName` attribute of the rule is `false`, the **actual record name** is taken as the record name
       with all the transformations specified by the generator and/or the record regional option.
    2. If the `useEventName` rule is `true`, the **actual name of the record** is taken as the value of the `eventName`
       attribute of the record.
3. If the **actual record name** does not match the regex specified by the `record` attribute, the rule does not match
   the given record and **does not affect** its visibility.
4. In all other cases, the rule **affects** the record visibility.

##### Visibility rule for one record field

1. If the rule does not have the `field` attribute and both the `include-tags` and `exclude-tags` filters are missing,
   the rule does not match the given field and **does not affect** its visibility. Further conditions are not checked.
2.
    1. If the `useEventName` attribute of the rule is `false`, the **actual parent record name** is taken as the name of
       the record including the field with all the transformations specified by the generator and/or the record regional
       option.
    2. If the `useEventName` rule is `true`, the **actual name of the parent record** is taken as the value of the
       `eventName` attribute of the field.
3. If the **actual parent record name** does not match the regex specified by the `record` attribute, the rule does not
   match the given field and **does not affect** its visibility. Further conditions are not checked.
4. If the field name does not match the regex specified by the `field` attribute, the rule does not match the given
   field and **does not affect** its visibility. Further conditions are not checked.
5. The field must have **all** tags included in the `include-tags` set. If not, the rule does not match the given field
   and **does not affect** its visibility. Further conditions are not checked.
6. The field must not have **any** tags from the `exclude-tags` set. If it does, the rule doesn't match the given field
   and **does not affect** its visibility. Further conditions are not checked.
7. In all other cases, the rule **affects** the field visibility.

Note that if the `include-tags` or `exclude-tags` set is empty, the corresponding conditions are automatically satisfied
for any set of tags that mark the field.
                                                      
