#!/data/data/com.termux/files/usr/bin/python3
"""
AXML Patcher — injects missing attributes into binary AndroidManifest.xml
that old aapt v1 can't process but modern Android requires.
"""

import struct
import zipfile
import sys
import os

def parse_axml(axml_data):
    """Parse AXML into structured form"""
    pos = 0
    magic = struct.unpack_from('<I', axml_data, pos)[0]
    size = struct.unpack_from('<I', axml_data, pos+4)[0]
    pos += 8
    
    # Parse string pool
    pool_type = struct.unpack_from('<I', axml_data, pos)[0]
    assert pool_type == 0x001c0001, f"Expected string pool, got {pool_type:#x}"
    pool_size = struct.unpack_from('<I', axml_data, pos+4)[0]
    str_count = struct.unpack_from('<I', axml_data, pos+8)[0]
    style_count = struct.unpack_from('<I', axml_data, pos+12)[0]
    flags = struct.unpack_from('<I', axml_data, pos+16)[0]
    str_data_start = struct.unpack_from('<I', axml_data, pos+20)[0]
    styles_data_start = struct.unpack_from('<I', axml_data, pos+24)[0]
    
    pool_start = pos
    off_table_pos = pos + 28
    str_data_pos = pool_start + str_data_start
    
    strings = []
    for i in range(str_count):
        off = struct.unpack_from('<I', axml_data, off_table_pos + i*4)[0]
        spos = str_data_pos + off
        if flags & 0x100:  # UTF-8
            strlen = struct.unpack_from('<B', axml_data, spos)[0]
            s = axml_data[spos+1:spos+1+strlen].decode('utf-8', errors='replace')
        else:  # UTF-16LE
            strlen = struct.unpack_from('<H', axml_data, spos)[0]
            s = axml_data[spos+2:spos+2+strlen*2].decode('utf-16-le', errors='replace')
        strings.append(s)
    
    pos = pool_start + pool_size
    
    # Parse resource ID chunk (optional)
    res_ids = []
    chunk_type = struct.unpack_from('<I', axml_data, pos)[0]
    if chunk_type == 0x00080180:
        res_size = struct.unpack_from('<I', axml_data, pos+4)[0]
        res_count = (res_size - 8) // 4
        for i in range(res_count):
            rid = struct.unpack_from('<I', axml_data, pos+8 + i*4)[0]
            res_ids.append(rid)
        pos += res_size
        chunk_type = struct.unpack_from('<I', axml_data, pos)[0]
    
    # Parse XML elements - skip namespace chunks
    assert chunk_type in (0x00100100, 0x00100102), f"Expected NS or START_ELEMENT, got {chunk_type:#x}"
    
    # Skip namespace declarations
    while chunk_type == 0x00100100:
        pos += struct.unpack_from('<I', axml_data, pos+4)[0]
        chunk_type = struct.unpack_from('<I', axml_data, pos)[0]
    
    assert chunk_type == 0x00100102, f"Expected START_ELEMENT, got {chunk_type:#x}"
    
    elements = []
    while pos < len(axml_data):
        elem = parse_element(axml_data, pos, strings)
        elements.append(elem)
        pos = elem['end']
        if elem['type'] == 'END_DOCUMENT':
            break
    
    return {
        'magic': magic,
        'size': size,
        'strings': strings,
        'pool_start': pool_start,
        'pool_size': pool_size,
        'pool_flags': flags,
        'str_data_start': str_data_start,
        'res_ids': res_ids,
        'elements': elements,
        'raw': axml_data
    }

def parse_element(data, pos, strings):
    """Parse a single XML element at position pos"""
    elem_type = struct.unpack_from('<I', data, pos)[0]
    elem_size = struct.unpack_from('<I', data, pos+4)[0]
    
    result = {'type': None, 'pos': pos, 'end': pos + elem_size, 'size': elem_size}
    
    if elem_type == 0x00100102:  # START_TAG
        line = struct.unpack_from('<I', data, pos+8)[0]
        comment_idx = struct.unpack_from('<I', data, pos+12)[0]
        ns_idx = struct.unpack_from('<I', data, pos+16)[0]
        name_idx = struct.unpack_from('<I', data, pos+20)[0]
        attr_start = struct.unpack_from('<H', data, pos+24)[0]
        attr_size = struct.unpack_from('<H', data, pos+26)[0]
        attr_count = struct.unpack_from('<H', data, pos+28)[0]
        id_attr = struct.unpack_from('<H', data, pos+30)[0]
        class_attr = struct.unpack_from('<H', data, pos+32)[0]
        style_attr = struct.unpack_from('<H', data, pos+34)[0]
        
        attrs = []
        for i in range(attr_count):
            apos = pos + attr_start + i * attr_size
            ns = struct.unpack_from('<I', data, apos)[0]
            name = struct.unpack_from('<I', data, apos+4)[0]
            value_str = struct.unpack_from('<I', data, apos+8)[0]
            type_val = struct.unpack_from('<I', data, apos+12)[0] >> 24
            value_data = struct.unpack_from('<I', data, apos+12)[0] & 0xFFFFFF
            
            attr_type = {1:'STRING', 2:'INT_DEC', 3:'INT_HEX', 4:'INT_BOOL',
                         16:'STRING_REF', 17:'ATTR_REF', 18:'REFERENCE'}.get(type_val, f'UNKNOWN({type_val})')
            
            attr_name = strings[name] if name < len(strings) else f"?{name}"
            attr_ns = strings[ns] if ns < len(strings) and ns >= 0 else None
            
            if type_val == 1:  # STRING
                attr_value = strings[value_data] if value_data < len(strings) else f"?{value_data}"
            elif type_val == 2:  # INT_DEC
                attr_value = str(value_data)
            elif type_val == 4:  # INT_BOOL
                attr_value = 'true' if value_data != 0 else 'false'
            elif type_val == 16:  # STRING_REF
                attr_value = strings[value_str] if value_str < len(strings) else f"?{value_str}"
            else:
                attr_value = f"0x{value_data:x}"
            
            attrs.append({
                'ns_idx': ns,
                'name_idx': name,
                'value_str_idx': value_str,
                'raw_type': type_val,
                'raw_value': value_data,
                'name': attr_name,
                'value': attr_value
            })
        
        ns_str = strings[ns_idx] if ns_idx >= 0 and ns_idx < len(strings) else None
        name_str = strings[name_idx] if name_idx < len(strings) else f"?{name_idx}"
        
        result.update({
            'type': 'START_TAG',
            'ns': ns_str,
            'name': name_str,
            'ns_idx': ns_idx,
            'name_idx': name_idx,
            'attrs': attrs,
            'attr_count': attr_count,
            'attr_start': attr_start,
            'attr_size': attr_size
        })
    
    elif elem_type == 0x00100103:  # END_TAG
        ns_idx = struct.unpack_from('<I', data, pos+8)[0]
        name_idx = struct.unpack_from('<I', data, pos+12)[0]
        name_str = strings[name_idx] if name_idx < len(strings) else f"?{name_idx}"
        result.update({
            'type': 'END_TAG',
            'ns_idx': ns_idx,
            'name_idx': name_idx,
            'name': name_str
        })
    
    elif elem_type == 0x00100104:  # END_DOCUMENT
    
        result.update({'type': 'END_DOCUMENT'})
    
    return result

def build_axml(parsed, new_attrs_for_element):
    """Rebuild AXML with additional attributes injected"""
    axml = parsed['raw']
    strings = parsed['strings']
    
    # New strings to add
    new_strings = [
        "exported",       # 27
        "configChanges",  # 28
        "launchMode",     # 29
        "singleTop",      # 30
        "orientation|screenSize",  # 31
        "allowBackup",    # 32
        "versionCode",    # 33
        "versionName",    # 34
        "required",       # 35
        "true",           # 36
        "false",          # 37
        "1",              # 38
        "2.0",            # 39
        "uses-feature",   # 40
        "android.hardware.bluetooth",  # 41
        "uses-sdk",       # 42
        "21",             # 43
        "35",             # 44
    ]
    
    # Check which strings already exist
    existing = set(strings)
    strings_to_add = [(i, s) for i, s in enumerate(new_strings) if s not in existing]
    
    if not strings_to_add:
        print("  All needed strings already exist in pool")
        return axml
    
    # Build new string pool data
    pool_start = parsed['pool_start']
    old_str_count = len(strings)
    new_str_count = old_str_count + len(strings_to_add)
    
    # Rebuild string pool
    # We need to rebuild the entire AXML because string pool size changes
    
    # First, rebuild string pool
    is_utf8 = bool(parsed['pool_flags'] & 0x100)
    
    new_str_offsets = []
    new_str_data = bytearray()
    
    for s in strings:
        off = len(new_str_data)
        new_str_offsets.append(off)
        if is_utf8:
            encoded = s.encode('utf-8')
            new_str_data.append(len(encoded))
            new_str_data.extend(encoded)
            new_str_data.append(0)
        else:
            encoded = s.encode('utf-16-le')
            # UTF-16LE: length in uint16, then data, then null
            new_str_data.extend(struct.pack('<H', len(s)))
            new_str_data.extend(encoded)
            new_str_data.extend(b'\x00\x00')
    
    # Add new strings
    for _, s in strings_to_add:
        off = len(new_str_data)
        new_str_offsets.append(off)
        if is_utf8:
            encoded = s.encode('utf-8')
            new_str_data.append(len(encoded))
            new_str_data.extend(encoded)
            new_str_data.append(0)
        else:
            encoded = s.encode('utf-16-le')
            new_str_data.extend(struct.pack('<H', len(s)))
            new_str_data.extend(encoded)
            new_str_data.extend(b'\x00\x00')
    
    # New pool size: header (28) + offset table (new_str_count * 4) + padding + string data
    new_pool_header_size = 28 + new_str_count * 4 + 0  # no styles
    # Pad to 4-byte boundary
    padding = (4 - (new_pool_header_size % 4)) % 4
    new_str_data_offset = new_pool_header_size + padding
    
    new_pool_size = new_str_data_offset + len(new_str_data)
    
    new_pool = bytearray()
    new_pool.extend(struct.pack('<I', 0x001c0001))
    new_pool.extend(struct.pack('<I', new_pool_size))
    new_pool.extend(struct.pack('<I', new_str_count))
    new_pool.extend(struct.pack('<I', 0))  # style_count
    new_pool.extend(struct.pack('<I', parsed['pool_flags']))
    new_pool.extend(struct.pack('<I', new_str_data_offset))
    new_pool.extend(struct.pack('<I', 0))  # styles_offset
    
    for off in new_str_offsets:
        new_pool.extend(struct.pack('<I', off))
    
    # Pad
    new_pool.extend(b'\x00' * padding)
    
    # String data
    new_pool.extend(new_str_data)
    
    # Now rebuild the whole AXML
    # Header (8 bytes) + new pool + res_ids + XML elements
    # The res_ids section doesn't change
    # The XML elements need to reference the new string indices
    
    # Old → new string index mapping
    old_to_new = list(range(old_str_count))
    for i, (orig_idx, _) in enumerate(strings_to_add):
        old_to_new.append(old_str_count + i)
    
    # Rebuild XML elements with updated string indices
    new_xml_parts = []
    old_end = parsed['pool_start'] + parsed['pool_size']
    
    # Copy res_ids unchanged (they reference resource IDs, not strings)
    pos = old_end
    if parsed['res_ids']:
        res_chunk_type = struct.unpack_from('<I', axml, pos)[0]
        res_chunk_size = struct.unpack_from('<I', axml, pos+4)[0]
        new_xml_parts.append(axml[pos:pos+res_chunk_size])
        pos += res_chunk_size
    
    # Now modify XML elements: update string references and add attributes
    while pos < len(axml):
        elem_type = struct.unpack_from('<I', axml, pos)[0]
        elem_size = struct.unpack_from('<I', axml, pos+4)[0]
        
        if elem_type == 0x00100102:  # START_TAG
            ns_idx = struct.unpack_from('<I', axml, pos+16)[0]
            name_idx = struct.unpack_from('<I', axml, pos+20)[0]
            attr_start = struct.unpack_from('<H', axml, pos+24)[0]
            attr_size = struct.unpack_from('<H', axml, pos+26)[0]
            attr_count = struct.unpack_from('<H', axml, pos+28)[0]
            
            name_str = strings[name_idx] if name_idx < len(strings) else ""
            
            # Build new attributes
            new_attrs = bytearray()
            attr_bidx = 0  # blue-print attribute index
            
            # Copy existing attributes with updated indices
            for i in range(attr_count):
                apos = pos + attr_start + i * attr_size
                attr_data = axml[apos:apos+attr_size]
                # Update ns, name, value_str indices if they changed
                # (they don't for string indices since we just appended)
                new_attrs.extend(attr_data)
                attr_bidx += 1
            
            # Add new attributes for this element
            elem_key = name_str
            if elem_key in new_attrs_for_element:
                for new_attr in new_attrs_for_element[elem_key]:
                    ns_str, attr_name, attr_value, attr_type = new_attr
                    
                    # Find string indices
                    try:
                        ns_idx_val = strings.index(ns_str)
                    except ValueError:
                        ns_idx_val = old_str_count + [s for _, s in strings_to_add].index(ns_str)
                    
                    try:
                        name_idx_val = strings.index(attr_name)
                    except ValueError:
                        name_idx_val = old_str_count + [s for _, s in strings_to_add].index(attr_name)
                    
                    try:
                        if attr_type == 'string':
                            val_idx_val = strings.index(attr_value)
                        else:
                            val_idx_val = 0
                    except ValueError:
                        if attr_type == 'string':
                            val_idx_val = old_str_count + [s for _, s in strings_to_add].index(attr_value)
                        else:
                            val_idx_val = 0
                    
                    attr_bytes = bytearray()
                    attr_bytes.extend(struct.pack('<I', ns_idx_val))  # ns
                    attr_bytes.extend(struct.pack('<I', name_idx_val))  # name
                    
                    if attr_type == 'string':
                        attr_bytes.extend(struct.pack('<I', val_idx_val))  # value_str
                        raw_type = 1  # TYPE_STRING
                        raw_value = val_idx_val
                    elif attr_type == 'int':
                        raw_type = 2  # TYPE_INT_DEC
                        raw_value = int(attr_value)
                        attr_bytes.extend(struct.pack('<I', 0))  # value_str placeholder
                    elif attr_type == 'bool':
                        raw_type = 4  # TYPE_INT_BOOL
                        raw_value = 1 if attr_value == 'true' else 0
                        attr_bytes.extend(struct.pack('<I', 0))
                    else:
                        raw_type = 1
                        raw_value = val_idx_val
                        attr_bytes.extend(struct.pack('<I', val_idx_val))
                    
                    # Res_value = 8 bytes: size(2) + res0(1) + dataType(1) + data(4)
                    attr_bytes.extend(struct.pack('<H', 8))  # size
                    attr_bytes.extend(struct.pack('<B', 0))  # res0
                    attr_bytes.extend(struct.pack('<B', raw_type))  # dataType
                    attr_bytes.extend(struct.pack('<I', raw_value))  # data
                    new_attrs.extend(attr_bytes)
                    attr_bidx += 1
            
            # Rebuild the element
            new_elem = bytearray()
            new_elem.extend(axml[pos:pos+8])  # type + size (will fix size later)
            new_elem.extend(axml[pos+8:pos+16])  # line + comment
            
            new_ns_idx = ns_idx
            new_name_idx = name_idx
            if isinstance(new_ns_idx, int) and new_ns_idx >= 0:
                pass  # keep as-is
            
            new_elem.extend(struct.pack('<I', new_ns_idx))
            new_elem.extend(struct.pack('<I', new_name_idx))
            
            # Fix attr_start (always 20*4 = 80 for 5 more fields: attr_start, attr_size, attr_count, id, class, style)
            # Actually attr_start is offset from element start to first attribute
            # Element header: type(4) + size(4) + line(4) + comment(4) + ns(4) + name(4) = 24 bytes
            # Then: attr_start(2) + attr_size(2) + attr_count(2) + id(2) + class(2) + style(2) = 12 bytes
            # Total: 36 bytes before attrs
            new_attr_start = 36
            new_attr_size_val = 20  # each attribute is 20 bytes
            new_attr_count_val = attr_bidx
            
            new_elem.extend(struct.pack('<H', new_attr_start))
            new_elem.extend(struct.pack('<H', new_attr_size_val))
            new_elem.extend(struct.pack('<H', new_attr_count_val))
            new_elem.extend(axml[pos+30:pos+36])  # id, class, style
            
            new_elem.extend(new_attrs)
            
            # Fix element size  
            new_elem_size = len(new_elem)
            struct.pack_into('<I', new_elem, 4, new_elem_size)
            
            new_xml_parts.append(bytes(new_elem))
            
        else:
            # Copy unchanged (END_TAG, END_DOCUMENT, TEXT, NAMESPACE_START, NAMESPACE_END)
            new_xml_parts.append(axml[pos:pos+elem_size])
        
        pos += elem_size
    
    # Build final AXML
    xml_body = b''.join(new_xml_parts)
    new_axml_size = 8 + new_pool_size + len(xml_body)
    
    result = bytearray()
    result.extend(struct.pack('<I', parsed['magic']))
    result.extend(struct.pack('<I', new_axml_size))
    result.extend(new_pool)
    result.extend(xml_body)
    
    return bytes(result)


def fix_manifest(apk_path):
    """Inject modern attributes into APK's binary manifest"""
    print(f"Patching {apk_path}...")
    
    # Read APK
    with zipfile.ZipFile(apk_path, 'r') as zf:
        axml_data = zf.read('AndroidManifest.xml')
        all_files = {name: zf.read(name) for name in zf.namelist()}
    
    # Parse
    parsed = parse_axml(axml_data)
    
    print(f"  Strings: {len(parsed['strings'])}")
    print(f"  Resource IDs: {len(parsed['res_ids'])}")
    print(f"  Elements: {len(parsed['elements'])}")
    
    # Show current elements
    for e in parsed['elements']:
        if e['type'] == 'START_TAG':
            print(f"  <{e['name']}> attrs={e['attr_count']}")
            for a in e['attrs']:
                print(f"    {a['name']}=\"{a['value']}\"")
    
    # Define attributes to inject
    # (namespace_string, attr_name, attr_value, attr_type)
    new_attrs = {
        'manifest': [
            ('android', 'versionCode', '1', 'int'),
            ('android', 'versionName', '2.0', 'string'),
        ],
        'activity': [
            ('android', 'exported', 'true', 'bool'),
            ('android', 'configChanges', 'orientation|screenSize', 'string'),
            ('android', 'launchMode', 'singleTop', 'string'),
        ],
        'application': [
            ('android', 'allowBackup', 'false', 'bool'),
        ],
    }
    
    # Rebuild
    new_axml = build_axml(parsed, new_attrs)
    
    print(f"\n  New AXML size: {len(new_axml)} (was {len(axml_data)})")
    
    # Write new APK
    out_path = apk_path.replace('.apk', '-fixed.apk')
    if out_path == apk_path:
        out_path = apk_path + '.fixed.apk'
    
    with zipfile.ZipFile(out_path, 'w', zipfile.ZIP_DEFLATED) as zf:
        # Write new manifest first
        zf.writestr('AndroidManifest.xml', new_axml)
        # Write rest of files
        for name, data in all_files.items():
            if name != 'AndroidManifest.xml':
                zf.writestr(name, data)
    
    print(f"  → {out_path}")
    
    # Verify
    with zipfile.ZipFile(out_path, 'r') as zf:
        new_axml_verify = zf.read('AndroidManifest.xml')
    parsed2 = parse_axml(new_axml_verify)
    print(f"\n  Verifying patched manifest:")
    for e in parsed2['elements']:
        if e['type'] == 'START_TAG':
            print(f"  <{e['name']}> attrs={e['attr_count']}")
            for a in e['attrs']:
                print(f"    {a['name']}=\"{a['value']}\"")
    
    return out_path

if __name__ == '__main__':
    apk = sys.argv[1] if len(sys.argv) > 1 else '/data/data/com.termux/files/home/bluetooth-spammer/build/apk/obliterate-bt-v2.0.apk'
    fix_manifest(apk)
